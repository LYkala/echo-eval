package main

import (
	"bufio"
	"encoding/binary"
	"fmt"
	"io"
	"net"
	"os"
	"strings"
	"syscall"
	"time"
	"unsafe"
)

const (
	listenAddr    = "127.0.0.1:18744"
	proxyAddr     = "10.0.2.2:8888"
	proxyAddrAlt  = "10.0.2.2:8888"
	soOriginalDst = 80 // Linux/Android SO_ORIGINAL_DST
)

// Raw TCP fallback for DDS/ASR/TTS when CONNECT proxy is down.
// Guest can reach 10.0.2.2:8736/8888. Prefer CONNECT first (see handle).
var hostTunnel = map[string]string{
	"asr.dui.ai":    "10.0.2.2:8736",
	"tts.dui.ai":    "10.0.2.2:8736",
	"dds.dui.ai":    "10.0.2.2:8736",
	"120.55.26.227": "10.0.2.2:8736",
}

var ipHost = map[string]string{
	"47.111.175.204": "auth.dui.ai",
	"120.55.26.227":  "asr.dui.ai", // also tts.dui.ai / dds.dui.ai
	"47.114.114.166": "push.duiopen.com",
	"47.114.96.117":  "tts.duiopen.com",
	"60.204.143.165": "log.aispeech.com",
	"124.222.5.240":  "124.222.5.240", // AISF ACM / ASR-TTS direct IP
}

// Prefer hostname labels when ORIGINAL_DST is this IP (SNI may be absent).
var ipHostPrefer = map[string]string{
	"47.111.175.204": "auth.dui.ai",
	"120.55.26.227":  "dds.dui.ai",
	"47.114.114.166": "push.duiopen.com",
	"47.114.96.117":  "tts.duiopen.com",
	"60.204.143.165": "log.aispeech.com",
	"124.222.5.240":  "124.222.5.240",
}

func originalDst(conn net.Conn) (string, int) {
	tc, ok := conn.(*net.TCPConn)
	if !ok {
		return "", 0
	}
	rc, err := tc.SyscallConn()
	if err != nil {
		return "", 0
	}
	var host string
	var port int
	var opErr error
	err = rc.Control(func(fd uintptr) {
		var addr syscall.RawSockaddrInet4
		size := uint32(unsafe.Sizeof(addr))
		_, _, errno := syscall.Syscall6(
			syscall.SYS_GETSOCKOPT,
			fd,
			uintptr(syscall.IPPROTO_IP),
			uintptr(soOriginalDst),
			uintptr(unsafe.Pointer(&addr)),
			uintptr(unsafe.Pointer(&size)),
			0,
		)
		if errno != 0 {
			opErr = errno
			return
		}
		ip := net.IPv4(addr.Addr[0], addr.Addr[1], addr.Addr[2], addr.Addr[3]).String()
		port = int(addr.Port>>8 | addr.Port<<8) // network byte order
		host = ip
	})
	if err != nil || opErr != nil {
		return "", 0
	}
	return host, port
}

func parseSNI(data []byte) string {
	if len(data) < 43 || data[0] != 0x16 {
		return ""
	}
	hs := data[5:]
	if len(hs) < 4 || hs[0] != 0x01 {
		return ""
	}
	body := hs[4:]
	p := 2 + 32
	if p >= len(body) {
		return ""
	}
	p += 1 + int(body[p])
	if p+2 > len(body) {
		return ""
	}
	cs := int(binary.BigEndian.Uint16(body[p : p+2]))
	p += 2 + cs
	if p >= len(body) {
		return ""
	}
	p += 1 + int(body[p])
	if p+2 > len(body) {
		return ""
	}
	extLen := int(binary.BigEndian.Uint16(body[p : p+2]))
	p += 2
	end := p + extLen
	if end > len(body) {
		end = len(body)
	}
	for p+4 <= end {
		et := binary.BigEndian.Uint16(body[p : p+2])
		elen := int(binary.BigEndian.Uint16(body[p+2 : p+4]))
		p += 4
		if et == 0 && elen >= 5 && p+elen <= end {
			q := p + 2 + 1
			nlen := int(binary.BigEndian.Uint16(body[q : q+2]))
			q += 2
			if nlen > 0 && q+nlen <= p+elen {
				return string(body[q : q+nlen])
			}
		}
		p += elen
	}
	return ""
}

func pipe(a, b net.Conn) {
	defer a.Close()
	defer b.Close()
	done := make(chan struct{}, 2)
	go func() { io.Copy(a, b); done <- struct{}{} }()
	go func() { io.Copy(b, a); done <- struct{}{} }()
	<-done
}

type readFirstConn struct {
	net.Conn
	first []byte
	off   int
}

func (c *readFirstConn) Read(p []byte) (int, error) {
	if c.off < len(c.first) {
		n := copy(p, c.first[c.off:])
		c.off += n
		return n, nil
	}
	return c.Conn.Read(p)
}

type bufReaderConn struct {
	net.Conn
	br *bufio.Reader
}

func (c *bufReaderConn) Read(p []byte) (int, error) {
	return c.br.Read(p)
}

func resolveHostPort(client net.Conn, clientHello []byte) (string, int) {
	ip, port := originalDst(client)
	if port <= 0 {
		port = 443
	}
	if sni := parseSNI(clientHello); sni != "" {
		return sni, port
	}
	if h, ok := ipHostPrefer[ip]; ok {
		fmt.Fprintln(os.Stderr, "ORIGINAL_DST", ip, "->", h)
		return h, port
	}
	if h, ok := ipHost[ip]; ok {
		fmt.Fprintln(os.Stderr, "ORIGINAL_DST", ip, "->", h)
		return h, port
	}
	// Loopback ORIGINAL_DST means app resolved to 127.0.0.1 (local-agent mode).
	// Never CONNECT to 127.0.0.1 via upstream proxy — it always fails.
	// Prefer dds.dui.ai: dial-test assistant traffic is mostly DDS, not auth.
	if ip == "127.0.0.1" || ip == "0.0.0.0" || ip == "::1" {
		fmt.Fprintln(os.Stderr, "ORIGINAL_DST loopback without SNI; fallback dds.dui.ai")
		return "dds.dui.ai", 443
	}
	// Fall back to original destination IP (public HTTPS via CONNECT).
	if ip != "" {
		fmt.Fprintln(os.Stderr, "ORIGINAL_DST", ip, "port", port)
		return ip, port
	}
	return "auth.dui.ai", 443
}

func handle(client net.Conn) {
	_ = client.SetDeadline(time.Now().Add(20 * time.Second))
	buf := make([]byte, 0, 4096)
	tmp := make([]byte, 2048)
	for len(buf) < 5 {
		n, err := client.Read(tmp)
		if err != nil || n == 0 {
			client.Close()
			return
		}
		buf = append(buf, tmp[:n]...)
	}
	// Non-TLS (plain WS/HTTP): do not wait for a TLS record; tunnel by ORIGINAL_DST.
	if buf[0] != 0x16 {
		ip, port := originalDst(client)
		if port <= 0 {
			port = 443
		}
		host := ip
		if host == "" || host == "127.0.0.1" {
			host = "124.222.5.240"
		}
		fmt.Fprintln(os.Stderr, "target-raw", host, ":", port)
		pipeRawConnect(client, buf, host, port)
		return
	}
	need := 5 + int(binary.BigEndian.Uint16(buf[3:5]))
	if need > 8192 {
		need = 8192
	}
	for len(buf) < need {
		_ = client.SetDeadline(time.Now().Add(3 * time.Second))
		n, err := client.Read(tmp)
		if err != nil || n == 0 {
			break
		}
		buf = append(buf, tmp[:n]...)
		if len(buf) >= need {
			break
		}
	}

	host, dstPort := resolveHostPort(client, buf)
	fmt.Fprintln(os.Stderr, "target", host, ":", dstPort)
	pipeTLSConnect(client, buf, host, dstPort)
}

func pipeRawConnect(client net.Conn, first []byte, host string, dstPort int) {
	var upstream net.Conn
	var br *bufio.Reader
	var lastErr error
	tryConnectVia := func(proxy string) bool {
		for attempt := 1; attempt <= 3; attempt++ {
			p, err := net.DialTimeout("tcp", proxy, 10*time.Second)
			if err != nil {
				lastErr = err
				time.Sleep(time.Duration(attempt) * 200 * time.Millisecond)
				continue
			}
			req := fmt.Sprintf("CONNECT %s:%d HTTP/1.1\r\nHost: %s:%d\r\n\r\n", host, dstPort, host, dstPort)
			if _, err := io.WriteString(p, req); err != nil {
				p.Close()
				lastErr = err
				continue
			}
			r := bufio.NewReader(p)
			status, err := r.ReadString('\n')
			if err != nil || !strings.Contains(status, "200") {
				lastErr = err
				fmt.Fprintln(os.Stderr, "CONNECT fail", proxy, strings.TrimSpace(status), err, "attempt", attempt)
				p.Close()
				continue
			}
			for {
				line, err := r.ReadString('\n')
				if err != nil || line == "\r\n" || line == "\n" {
					break
				}
			}
			upstream = p
			br = r
			fmt.Fprintln(os.Stderr, "CONNECT ok", host, ":", dstPort, "via", proxy)
			return true
		}
		return false
	}
	if !tryConnectVia(proxyAddr) && !tryConnectVia(proxyAddrAlt) {
		fmt.Fprintln(os.Stderr, "upstream give up", host, lastErr)
		client.Close()
		return
	}
	_ = client.SetDeadline(time.Time{})
	_ = upstream.SetDeadline(time.Time{})
	var right net.Conn = upstream
	if br != nil {
		right = &bufReaderConn{Conn: upstream, br: br}
	}
	pipe(&readFirstConn{Conn: client, first: first}, right)
}

func pipeTLSConnect(client net.Conn, buf []byte, host string, dstPort int) {
	var upstream net.Conn
	var br *bufio.Reader
	var lastErr error

	tryTunnel := func() bool {
		tun, ok := hostTunnel[host]
		if !ok {
			return false
		}
		for attempt := 1; attempt <= 3; attempt++ {
			p, err := net.DialTimeout("tcp", tun, 5*time.Second)
			if err != nil {
				lastErr = err
				fmt.Fprintln(os.Stderr, "tunnel dial", tun, err, "attempt", attempt)
				time.Sleep(time.Duration(attempt) * 150 * time.Millisecond)
				continue
			}
			upstream = p
			fmt.Fprintln(os.Stderr, "TUNNEL ok", host, "via", tun)
			return true
		}
		fmt.Fprintln(os.Stderr, "tunnel failed, fallback CONNECT", host, lastErr)
		return false
	}

	tryConnectVia := func(proxy string) bool {
		for attempt := 1; attempt <= 3; attempt++ {
			p, err := net.DialTimeout("tcp", proxy, 10*time.Second)
			if err != nil {
				lastErr = err
				fmt.Fprintln(os.Stderr, "proxy dial", proxy, err, "attempt", attempt)
				time.Sleep(time.Duration(attempt) * 200 * time.Millisecond)
				continue
			}
			req := fmt.Sprintf("CONNECT %s:%d HTTP/1.1\r\nHost: %s:%d\r\n\r\n", host, dstPort, host, dstPort)
			if _, err := io.WriteString(p, req); err != nil {
				p.Close()
				lastErr = err
				fmt.Fprintln(os.Stderr, "CONNECT write", proxy, err, "attempt", attempt)
				time.Sleep(time.Duration(attempt) * 200 * time.Millisecond)
				continue
			}
			r := bufio.NewReader(p)
			status, err := r.ReadString('\n')
			if err != nil || !strings.Contains(status, "200") {
				lastErr = err
				fmt.Fprintln(os.Stderr, "CONNECT fail", proxy, strings.TrimSpace(status), err, "attempt", attempt)
				p.Close()
				time.Sleep(time.Duration(attempt) * 200 * time.Millisecond)
				continue
			}
			for {
				line, err := r.ReadString('\n')
				if err != nil || line == "\r\n" || line == "\n" {
					break
				}
			}
			upstream = p
			br = r
			fmt.Fprintln(os.Stderr, "CONNECT ok", host, ":", dstPort, "via", proxy)
			return true
		}
		return false
	}

	tryConnect := func() bool {
		if tryConnectVia(proxyAddr) {
			return true
		}
		return tryConnectVia(proxyAddrAlt)
	}

	// Prefer CONNECT (stable WSS through jump proxy); raw tunnel is fallback.
	if !tryConnect() {
		_ = tryTunnel()
	}
	if upstream == nil {
		fmt.Fprintln(os.Stderr, "upstream give up", host, lastErr)
		client.Close()
		return
	}

	_ = client.SetDeadline(time.Time{})
	_ = upstream.SetDeadline(time.Time{})
	var right net.Conn = upstream
	if br != nil {
		right = &bufReaderConn{Conn: upstream, br: br}
	}
	pipe(&readFirstConn{Conn: client, first: buf}, right)
}

var dnsRecords = map[string]string{
	"dds.dui.ai":       "120.55.26.227",
	"asr.dui.ai":       "120.55.26.227",
	"tts.dui.ai":       "120.55.26.227",
	"auth.dui.ai":      "47.111.175.204",
	"push.duiopen.com": "47.114.114.166",
	"log.aispeech.com": "60.204.143.165",
}

func parseDNSName(msg []byte, off int) (string, int, error) {
	labels := []string{}
	jumped := false
	orig := off
	for {
		if off >= len(msg) {
			return "", 0, fmt.Errorf("bad name")
		}
		l := int(msg[off])
		if l == 0 {
			off++
			break
		}
		if l&0xC0 == 0xC0 {
			if off+1 >= len(msg) {
				return "", 0, fmt.Errorf("bad ptr")
			}
			ptr := int(l&0x3F)<<8 | int(msg[off+1])
			if !jumped {
				orig = off + 2
			}
			off = ptr
			jumped = true
			continue
		}
		off++
		if off+l > len(msg) {
			return "", 0, fmt.Errorf("bad label")
		}
		labels = append(labels, string(msg[off:off+l]))
		off += l
	}
	end := off
	if jumped {
		end = orig
	}
	return strings.ToLower(strings.Join(labels, ".")), end, nil
}

func buildDNSResponse(req []byte) []byte {
	if len(req) < 12 {
		return nil
	}
	name, qend, err := parseDNSName(req, 12)
	if err != nil || qend+4 > len(req) {
		return nil
	}
	qtype := binary.BigEndian.Uint16(req[qend : qend+2])
	question := req[12 : qend+4]
	tid := req[0:2]
	ip := dnsRecords[name]
	if ip == "" || qtype != 1 {
		out := append(append([]byte{}, tid...), 0x81, 0x83)
		out = append(out, []byte{0, 1, 0, 0, 0, 0, 0, 0}...)
		out = append(out, question...)
		return out
	}
	ip4 := net.ParseIP(ip).To4()
	if ip4 == nil {
		return nil
	}
	out := append(append([]byte{}, tid...), 0x81, 0x80)
	out = append(out, []byte{0, 1, 0, 1, 0, 0, 0, 0}...)
	out = append(out, question...)
	// pointer to qname at offset 12
	out = append(out, 0xC0, 0x0C)
	tmp := make([]byte, 10)
	binary.BigEndian.PutUint16(tmp[0:2], 1)  // A
	binary.BigEndian.PutUint16(tmp[2:4], 1)  // IN
	binary.BigEndian.PutUint32(tmp[4:8], 60) // TTL
	binary.BigEndian.PutUint16(tmp[8:10], 4) // rdlen
	out = append(out, tmp...)
	out = append(out, ip4...)
	return out
}

func serveDNS(addr string) {
	pc, err := net.ListenPacket("udp", addr)
	if err != nil {
		fmt.Fprintln(os.Stderr, "dns listen", addr, err)
		return
	}
	fmt.Fprintln(os.Stderr, "DNS READY", addr)
	buf := make([]byte, 1500)
	for {
		n, raddr, err := pc.ReadFrom(buf)
		if err != nil || n < 12 {
			continue
		}
		resp := buildDNSResponse(buf[:n])
		if resp != nil {
			_, _ = pc.WriteTo(resp, raddr)
		}
	}
}

func main() {
	go serveDNS("127.0.0.1:5353")
	go serveDNS("127.0.0.1:53")
	ln, err := net.Listen("tcp", listenAddr)
	if err != nil {
		fmt.Fprintln(os.Stderr, "listen", err)
		os.Exit(1)
	}
	fmt.Fprintln(os.Stderr, "READY", listenAddr, "via", proxyAddr)
	for {
		c, err := ln.Accept()
		if err != nil {
			continue
		}
		fmt.Fprintln(os.Stderr, "ACC", c.RemoteAddr())
		go handle(c)
	}
}
