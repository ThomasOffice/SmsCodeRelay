"""Winsock2 蓝牙 RFCOMM 服务端 - 直接监听 SPP 连接，绕过 COM 端口。

其他软件扫描/占用 COM 端口完全不影响此模块。
Android 端用 createRfcommSocketToServiceRecord(SPP_UUID) 直接连。
"""
import ctypes
import logging
import threading
import time

log = logging.getLogger("bt_rfcomm")

ws2_32 = ctypes.windll.ws2_32

AF_BTH = 32
BTPROTO_RFCOMM = 3
SOCK_STREAM = 1
INVALID_SOCKET = ~0
SOCKET_ERROR = -1
FIONBIO = 0x8004667E

WSAEWOULDBLOCK = 10035
WSAECONNRESET = 10054
WSAECONNABORTED = 10053

# SPP UUID: 00001101-0000-1000-8000-00805F9B34FB (little-endian GUID)
SPP_UUID_BYTES = bytes([
    0x01, 0x11, 0x00, 0x00, 0x00, 0x00, 0x00, 0x10,
    0x80, 0x00, 0x00, 0x80, 0x5F, 0x9B, 0x34, 0xFB
])


class SOCKADDR_BTH(ctypes.Structure):
    _pack_ = 1
    _fields_ = [
        ("addressFamily", ctypes.c_ushort),
        ("btAddr", ctypes.c_ulonglong),
        ("serviceClassId", ctypes.c_byte * 16),
        ("port", ctypes.c_ulong),
    ]


class WSADATA(ctypes.Structure):
    _pack_ = 1
    _fields_ = [
        ("wVersion", ctypes.c_ushort),
        ("wHighVersion", ctypes.c_ushort),
        ("szDescription", ctypes.c_char * 257),
        ("szSystemStatus", ctypes.c_char * 129),
        ("iMaxSockets", ctypes.c_ushort),
        ("iMaxUdpDg", ctypes.c_ushort),
        ("lpVendorInfo", ctypes.c_char * 2),
    ]


_wsa_started = False


def _ensure_wsa():
    global _wsa_started
    if _wsa_started:
        return
    wsa = WSADATA()
    ret = ws2_32.WSAStartup(ctypes.c_ushort(0x0202), ctypes.byref(wsa))
    if ret != 0:
        raise OSError(f"WSAStartup failed: {ret}")
    _wsa_started = True


class BtRfcommServer(threading.Thread):
    """蓝牙 RFCOMM 服务端线程。直接用 Winsock2 监听，不依赖 COM 端口。"""

    def __init__(self, handler, stop_event: threading.Event):
        super().__init__(daemon=True)
        self.handler = handler
        self.stop_event = stop_event
        self.server_sock = None
        self.client_sock = None
        self.listening = False
        self.connected = False

    def _create_and_listen(self) -> bool:
        _ensure_wsa()
        sock = ws2_32.socket(AF_BTH, SOCK_STREAM, BTPROTO_RFCOMM)
        if sock == INVALID_SOCKET:
            log.error("创建蓝牙 socket 失败: %d", ws2_32.WSAGetLastError())
            return False

        addr = SOCKADDR_BTH()
        addr.addressFamily = AF_BTH
        addr.btAddr = 0
        addr.port = 0
        for i in range(16):
            addr.serviceClassId[i] = SPP_UUID_BYTES[i]

        if ws2_32.bind(sock, ctypes.byref(addr), ctypes.c_int(ctypes.sizeof(addr))) == SOCKET_ERROR:
            log.error("bind 失败: %d", ws2_32.WSAGetLastError())
            ws2_32.closesocket(sock)
            return False

        if ws2_32.listen(sock, 1) == SOCKET_ERROR:
            log.error("listen 失败: %d", ws2_32.WSAGetLastError())
            ws2_32.closesocket(sock)
            return False

        # 设为非阻塞
        ws2_32.ioctlsocket(sock, FIONBIO, ctypes.byref(ctypes.c_ulong(1)))

        self.server_sock = sock
        self.listening = True
        log.info("蓝牙 RFCOMM 服务端已启动 (Winsock2 直连，绕过 COM 端口)")
        return True

    def _accept_client(self) -> bool:
        client = ws2_32.accept(self.server_sock, None, None)
        if client == INVALID_SOCKET:
            err = ws2_32.WSAGetLastError()
            if err == WSAEWOULDBLOCK:
                return False
            log.warning("accept 失败: %d", err)
            return False
        self.client_sock = client
        self.connected = True
        log.info("Android 设备已连接")
        return True

    def _recv_data(self) -> bytes:
        buf = ctypes.create_string_buffer(4096)
        n = ws2_32.recv(self.client_sock, buf, 4096, 0)
        if n == SOCKET_ERROR:
            err = ws2_32.WSAGetLastError()
            if err == WSAEWOULDBLOCK:
                return b""
            log.info("连接断开 (recv err=%d)", err)
            self._close_client()
            return b""
        if n == 0:
            log.info("连接已关闭 (对端)")
            self._close_client()
            return b""
        return buf.raw[:n]

    def _close_client(self):
        if self.client_sock:
            ws2_32.closesocket(self.client_sock)
            self.client_sock = None
        self.connected = False

    def _close_server(self):
        self._close_client()
        if self.server_sock:
            ws2_32.closesocket(self.server_sock)
            self.server_sock = None
        self.listening = False

    def force_reconnect(self):
        """强制重置连接状态，重新开始监听。"""
        log.info("强制重连: 重置 Winsock2 蓝牙服务端")
        self._close_server()
        time.sleep(0.3)

    def run(self):
        buf = b""
        while not self.stop_event.is_set():
            if self.server_sock is None:
                if not self._create_and_listen():
                    time.sleep(3)
                    continue

            if self.client_sock is None:
                self._accept_client()
                time.sleep(0.1)
                continue

            try:
                data = self._recv_data()
                if data:
                    buf += data
                    while b"\n" in buf:
                        line, buf = buf.split(b"\n", 1)
                        line = line.strip()
                        if line:
                            try:
                                text = line.decode("utf-8", errors="replace")
                            except Exception:
                                text = line.decode("latin-1", errors="replace")
                            log.debug("收到: %s", text[:120])
                            self.handler.handle(text)
                else:
                    time.sleep(0.05)
            except Exception as e:
                log.error("接收异常: %s", e)
                self._close_client()
                time.sleep(0.5)

        self._close_server()
        if _wsa_started:
            ws2_32.WSACleanup()
