package com.example.btkeyboard

import android.Manifest
import android.app.Activity
import android.bluetooth.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.*

class MainActivity : Activity() {

    private var hid: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private lateinit var status: TextView
    private var mods = 0
    private val toggles = mutableListOf<ToggleButton>()
    private val perms = arrayOf(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE
    )

    private val DESC = byteArrayOf(
        0x05, 0x01, 0x09, 0x06, 0xA1.toByte(), 0x01, 0x85.toByte(), 0x01,
        0x05, 0x07, 0x19, 0xE0.toByte(), 0x29, 0xE7.toByte(), 0x15, 0x00, 0x25, 0x01,
        0x75, 0x01, 0x95.toByte(), 0x08, 0x81.toByte(), 0x02,
        0x95.toByte(), 0x01, 0x75, 0x08, 0x81.toByte(), 0x01,
        0x95.toByte(), 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65,
        0x05, 0x07, 0x19, 0x00, 0x29, 0x65, 0x81.toByte(), 0x00, 0xC0.toByte(),
        0x05, 0x0C, 0x09, 0x01, 0xA1.toByte(), 0x01, 0x85.toByte(), 0x02,
        0x15, 0x00, 0x26, 0xFF.toByte(), 0x03, 0x19, 0x00, 0x2A, 0xFF.toByte(), 0x03,
        0x75, 0x10, 0x95.toByte(), 0x01, 0x81.toByte(), 0x00, 0xC0.toByte()
    )

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(d: BluetoothDevice?, ok: Boolean) {
            runOnUiThread {
                status.text = if (ok) "HID tayari. Unganisha kutoka iPad."
                else "HID HAIPO kwenye simu hii"
            }
        }

        override fun onConnectionStateChanged(d: BluetoothDevice?, s: Int) {
            if (s == BluetoothProfile.STATE_CONNECTED) host = d
            if (s == BluetoothProfile.STATE_DISCONNECTED) host = null
            runOnUiThread {
                status.text = if (host != null) "Imeunganishwa: ${d?.name}" else "Haijaunganishwa"
            }
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        buildUi()
        if (checkSelfPermission(perms[0]) == PackageManager.PERMISSION_GRANTED) startHid()
        else requestPermissions(perms, 1)
    }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, r: IntArray) {
        if (r.isNotEmpty() && r.all { it == PackageManager.PERMISSION_GRANTED }) startHid()
        else status.text = "Ruhusa za Bluetooth zimekataliwa. Zikubali kwenye Settings."
    }

    override fun onDestroy() {
        try { hid?.unregisterApp() } catch (e: Exception) {}
        super.onDestroy()
    }

    private fun startHid() {
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        adapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                hid = proxy as BluetoothHidDevice
                hid!!.registerApp(
                    BluetoothHidDeviceAppSdpSettings(
                        "Redmi Keyboard", "Keyboard", "Me",
                        BluetoothHidDevice.SUBCLASS1_KEYBOARD, DESC
                    ),
                    null, null, mainExecutor, callback
                )
            }

            override fun onServiceDisconnected(p: Int) { hid = null }
        }, BluetoothProfile.HID_DEVICE)
    }

    // ---------- UI ----------
    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        status = TextView(this).apply { text = "Inaanza..." }
        val input = EditText(this).apply {
            hint = "Andika hapa, inatumwa kwa iPad"
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        root.addView(status)
        root.addView(input)

        input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, bf: Int, c: Int) {
                if (c > bf) s?.substring(st + bf, st + c)?.forEach { sendChar(it) }
                else if (bf > c) repeat(bf - c) { press(0x2A) }
            }
        })

        row(root, "Fanya simu ionekane (pair)" to {
            startActivity(
                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                    .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            )
        })

        // Modifiers zinazoshikika (bonyeza kwanza, kisha herufi/kitufe)
        val mrow = LinearLayout(this)
        toggle(mrow, "Shift", 0x02)
        toggle(mrow, "Ctrl", 0x01)
        toggle(mrow, "Opt", 0x04)
        toggle(mrow, "Cmd", 0x08)
        root.addView(mrow)

        row(root, "Esc" to { press(0x29) }, "Tab" to { press(0x2B) },
            "Enter" to { press(0x28) }, "Bksp" to { press(0x2A) })
        row(root, "Space" to { press(0x2C) }, "Caps" to { press(0x39) },
            "Del" to { press(0x4C) })
        row(root, "←" to { press(0x50) }, "↑" to { press(0x52) },
            "↓" to { press(0x51) }, "→" to { press(0x4F) })
        row(root, "Home" to { press(0x4A) }, "End" to { press(0x4D) },
            "PgUp" to { press(0x4B) }, "PgDn" to { press(0x4E) })

        row(root, "Spotlight" to { press(0x2C, 0x08) },
            "Cmd+Tab" to { press(0x2B, 0x08) },
            "Cmd+H" to { press(0x0B, 0x08) })

        row(root, "Vol +" to { media(0xE9) }, "Vol -" to { media(0xEA) },
            "Mute" to { media(0xE2) })
        row(root, "Prev" to { media(0xB6) }, "Play" to { media(0xCD) },
            "Next" to { media(0xB5) })
        row(root, "Bright +" to { media(0x6F) }, "Bright -" to { media(0x70) })

        for (r in 0 until 2) {
            val items = Array<Pair<String, () -> Unit>>(6) { i ->
                val n = r * 6 + i + 1
                Pair("F$n") { press(0x39 + n) }
            }
            row(root, *items)
        }

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun row(root: LinearLayout, vararg items: Pair<String, () -> Unit>) {
        val r = LinearLayout(this)
        for ((t, f) in items) {
            val btn = Button(this).apply {
                text = t
                isAllCaps = false
                textSize = 12f
                setOnClickListener { f() }
            }
            r.addView(btn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(r)
    }

    private fun toggle(r: LinearLayout, name: String, bit: Int) {
        val t = ToggleButton(this).apply {
            textOn = name
            textOff = name
            text = name
            setOnCheckedChangeListener { _, on ->
                mods = if (on) mods or bit else mods and bit.inv()
            }
        }
        toggles.add(t)
        r.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    // ---------- HID ----------
    private fun press(k: Int, extra: Int = 0) {
        val h = host
        if (h != null) {
            hid?.sendReport(h, 1, byteArrayOf((mods or extra).toByte(), 0, k.toByte(), 0, 0, 0, 0, 0))
            hid?.sendReport(h, 1, ByteArray(8))
        }
        mods = 0
        toggles.forEach { it.isChecked = false }
    }

    private fun media(code: Int) {
        val h = host ?: return
        hid?.sendReport(h, 2, byteArrayOf((code and 0xFF).toByte(), (code shr 8).toByte()))
        hid?.sendReport(h, 2, byteArrayOf(0, 0))
    }

    private val plain = "-=[]\\;'`,./"
    private val shifted = "_+{}|:\"~<>?"
    private val symCodes = intArrayOf(0x2D, 0x2E, 0x2F, 0x30, 0x31, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38)
    private val numShift = "!@#\$%^&*()"

    private fun sendChar(c: Char) {
        when {
            c in 'a'..'z' -> press(0x04 + (c - 'a'))
            c in 'A'..'Z' -> press(0x04 + (c - 'A'), 0x02)
            c in '1'..'9' -> press(0x1E + (c - '1'))
            c == '0' -> press(0x27)
            c == ' ' -> press(0x2C)
            c == '\n' -> press(0x28)
            numShift.indexOf(c) >= 0 -> {
                val i = numShift.indexOf(c)
                press(if (i == 9) 0x27 else 0x1E + i, 0x02)
            }
            plain.indexOf(c) >= 0 -> press(symCodes[plain.indexOf(c)])
            shifted.indexOf(c) >= 0 -> press(symCodes[shifted.indexOf(c)], 0x02)
        }
    }
}
