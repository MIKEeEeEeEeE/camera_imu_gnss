package com.example.myapplication

import java.io.BufferedWriter
import java.io.FileWriter

class CSVLogger(path: String) {
    private val writer: BufferedWriter = FileWriter(path).buffered()
    @Synchronized
    fun log(vararg args: Any?) {
        writer.write(args.joinToString(","))
        writer.newLine()
    }
    @Synchronized
    fun close() {
        writer.close()
    }
}