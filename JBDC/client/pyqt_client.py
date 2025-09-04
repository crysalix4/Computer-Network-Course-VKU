import sys
import json
from PyQt5.QtCore import Qt, QByteArray
from PyQt5.QtWidgets import (
    QApplication, QMainWindow, QWidget, QLineEdit, QPushButton, QLabel,
    QHBoxLayout, QVBoxLayout, QTableWidget, QTableWidgetItem, QTextEdit,
    QMessageBox, QHeaderView
)
from PyQt5.QtNetwork import QTcpSocket, QAbstractSocket


class PyQtTcpClient(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("JBDC PyQt Client (Realtime)")
        self.resize(900, 600)
        self.edHost = QLineEdit("127.0.0.1")
        self.edPort = QLineEdit("9090")
        self.btnConn = QPushButton("Connect")
        self.btnDisc = QPushButton("Disconnect")
        self.lblStatus = QLabel("Disconnected")
        self.lblOnline = QLabel("Online: ?")

        self.btnConn.clicked.connect(self.do_connect)
        self.btnDisc.clicked.connect(self.do_disconnect)

        top = QHBoxLayout()
        top.addWidget(QLabel("Host:"))
        top.addWidget(self.edHost)
        top.addWidget(QLabel("Port:"))
        top.addWidget(self.edPort)
        top.addWidget(self.btnConn)
        top.addWidget(self.btnDisc)
        top.addStretch(1)
        top.addWidget(self.lblOnline)
        top.addWidget(self.lblStatus)
        self.tbl = QTableWidget(0, 3)
        self.tbl.setHorizontalHeaderLabels(["Mã", "Tên", "SĐT"])
        self.tbl.horizontalHeader().setSectionResizeMode(QHeaderView.Stretch)
        self.tbl.setSelectionBehavior(self.tbl.SelectRows)
        self.tbl.setEditTriggers(self.tbl.NoEditTriggers)
        self.edMa = QLineEdit()
        self.edTen = QLineEdit()
        self.edSdt = QLineEdit()
        self.btnInsert = QPushButton("Insert")
        self.btnDelete = QPushButton("Delete (by Mã)")
        self.btnRefresh = QPushButton("Refresh (GET_ALL)")

        self.btnInsert.clicked.connect(self.do_insert)
        self.btnDelete.clicked.connect(self.do_delete)
        self.btnRefresh.clicked.connect(lambda: self.send_cmd("GET_ALL"))

        form = QHBoxLayout()
        form.addWidget(QLabel("Mã:"))
        form.addWidget(self.edMa)
        form.addWidget(QLabel("Tên:"))
        form.addWidget(self.edTen)
        form.addWidget(QLabel("SĐT:"))
        form.addWidget(self.edSdt)
        form.addWidget(self.btnInsert)
        form.addWidget(self.btnDelete)
        form.addWidget(self.btnRefresh)
        self.log = QTextEdit()
        self.log.setReadOnly(True)
        root = QVBoxLayout()
        root.addLayout(top)
        root.addWidget(self.tbl)
        root.addLayout(form)
        root.addWidget(QLabel("Log:"))
        root.addWidget(self.log)

        cw = QWidget()
        cw.setLayout(root)
        self.setCentralWidget(cw)
        self.sock = QTcpSocket(self)
        self.sock.readyRead.connect(self.on_ready_read)
        self.sock.connected.connect(self.on_connected)
        self.sock.disconnected.connect(self.on_disconnected)
        self.sock.errorOccurred.connect(self.on_error)
        self.rows = {}

    def do_connect(self):
        if self.sock.state() == QAbstractSocket.ConnectedState:
            return
        host = self.edHost.text().strip()
        try:
            port = int(self.edPort.text().strip())
        except ValueError:
            QMessageBox.warning(self, "Lỗi", "Port không hợp lệ")
            return
        self.lblStatus.setText("Connecting...")
        self.sock.connectToHost(host, port)

    def do_disconnect(self):
        if self.sock.state() != QAbstractSocket.UnconnectedState:
            self.sock.disconnectFromHost()

    def on_connected(self):
        self.lblStatus.setText("Connected")
        self.log_append("[connected]")
        self.send_cmd("GET_ALL")

    def on_disconnected(self):
        self.lblStatus.setText("Disconnected")
        self.log_append("[disconnected]")

    def on_error(self, err):
        self.lblStatus.setText(f"Error: {self.sock.errorString()}")
        self.log_append(f"[error] {self.sock.errorString()}")

    def send_cmd(self, cmd: str):
        if self.sock.state() != QAbstractSocket.ConnectedState:
            QMessageBox.warning(self, "Chưa kết nối", "Hãy Connect trước.")
            return
        data = (cmd + "\n").encode("utf-8")
        self.sock.write(QByteArray(data))
        self.sock.flush()
        self.log_append(f">>> {cmd}")

    def on_ready_read(self):
        while self.sock.canReadLine():
            raw = bytes(self.sock.readLine()).decode("utf-8").rstrip("\r\n")
            if not raw:
                continue
            if raw.startswith("EVENT "):
                payload = raw[6:]
                self.handle_event(payload)
            else:
                self.handle_response(raw)

    def handle_event(self, payload: str):
        try:
            ev = json.loads(payload)
        except Exception as e:
            self.log_append(f"[bad EVENT] {payload} ({e})")
            return

        t = ev.get("type")
        if t == "join":
            peer = ev.get("peer")
            online = ev.get("online")
            if online is not None:
                self.lblOnline.setText(f"Online: {online}")
            self.log_append(f"[join] {peer} (online={online})")

        elif t == "leave":
            peer = ev.get("peer")
            online = ev.get("online")
            if online is not None:
                self.lblOnline.setText(f"Online: {online}")
            self.log_append(f"[leave] {peer} (online={online})")

        elif t == "insert":
            row = ev.get("row") or {}
            ma, ten, sdt = row.get("ma"), row.get("ten"), row.get("sdt")
            by = ev.get("by", "?")
            if ma:
                self.rows[ma] = (ten, sdt)
                self.upsert_row_in_table(ma, ten, sdt)
            self.log_append(f"[insert by {by}] {ma} | {ten} | {sdt}")

        elif t == "delete":
            ma = ev.get("ma")
            by = ev.get("by", "?")
            if ma and ma in self.rows:
                del self.rows[ma]
                self.remove_row_in_table(ma)
            self.log_append(f"[delete by {by}] {ma}")
        elif t == "get_all":
            by = ev.get("by", "?")
            count = ev.get("count")
            ms = ev.get("ms")
            self.log_append(f"[get_all by {by}] count={count}, {ms}ms")

        elif t == "get":
            by = ev.get("by", "?")
            ma = ev.get("ma")
            hit = ev.get("hit")
            ms = ev.get("ms")
            self.log_append(f"[get by {by}] ma={ma}, hit={hit}, {ms}ms")

        else:
            self.log_append(f"[event] {payload}")

    def handle_response(self, raw: str):
        text = raw.strip()
        if text.startswith("{") or text.startswith("["):
            try:
                obj = json.loads(text)
                if isinstance(obj, dict) and obj.get("ok") and isinstance(obj.get("data"), list):
                    self.rows.clear()
                    for it in obj["data"]:
                        ma, ten, sdt = it.get("ma"), it.get(
                            "ten"), it.get("sdt")
                        if ma:
                            self.rows[ma] = (ten, sdt)
                    self.reload_table()
                self.log_append(json.dumps(obj, ensure_ascii=False))
                return
            except Exception:
                pass
        self.log_append(text)

    def log_append(self, s: str):
        self.log.append(s)

    def reload_table(self):
        self.tbl.setRowCount(0)
        for ma, (ten, sdt) in sorted(self.rows.items()):
            r = self.tbl.rowCount()
            self.tbl.insertRow(r)
            self.tbl.setItem(r, 0, QTableWidgetItem(ma))
            self.tbl.setItem(r, 1, QTableWidgetItem(ten or ""))
            self.tbl.setItem(r, 2, QTableWidgetItem(sdt or ""))

    def upsert_row_in_table(self, ma, ten, sdt):
        for r in range(self.tbl.rowCount()):
            if self.tbl.item(r, 0) and self.tbl.item(r, 0).text() == ma:
                self.tbl.item(r, 1).setText(ten or "")
                self.tbl.item(r, 2).setText(sdt or "")
                return
        r = self.tbl.rowCount()
        self.tbl.insertRow(r)
        self.tbl.setItem(r, 0, QTableWidgetItem(ma))
        self.tbl.setItem(r, 1, QTableWidgetItem(ten or ""))
        self.tbl.setItem(r, 2, QTableWidgetItem(sdt or ""))

    def remove_row_in_table(self, ma):
        for r in range(self.tbl.rowCount()):
            if self.tbl.item(r, 0) and self.tbl.item(r, 0).text() == ma:
                self.tbl.removeRow(r)
                return

    def do_insert(self):
        ma = self.edMa.text().strip()
        ten = self.edTen.text().strip()
        sdt = self.edSdt.text().strip()
        if not ma or not ten or not sdt:
            QMessageBox.information(
                self, "Thiếu dữ liệu", "Nhập đủ Mã, Tên, SĐT")
            return
        self.send_cmd(f"INSERT {ma}|{ten}|{sdt}")

    def do_delete(self):
        ma = self.edMa.text().strip()
        if not ma:
            QMessageBox.information(self, "Thiếu mã", "Nhập Mã để xóa")
            return
        self.send_cmd(f"DELETE {ma}")


def main():
    app = QApplication(sys.argv)
    w = PyQtTcpClient()
    w.show()
    sys.exit(app.exec_())


if __name__ == "__main__":
    main()
