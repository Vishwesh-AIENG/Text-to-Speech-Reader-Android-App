package com.app.ttsreader.gl

import android.opengl.GLES20

/**
 * Wraps a compiled and linked GLSL program.
 *
 * Compile → link → delete shader objects (keeping only the program handle).
 * Throws [IllegalStateException] on compile or link failure so GL errors are
 * surfaced immediately rather than silently producing a black screen.
 */
class ShaderProgram(vertSrc: String, fragSrc: String) {

    val programId: Int

    init {
        val vert = compileShader(GLES20.GL_VERTEX_SHADER,   vertSrc)
        val frag = compileShader(GLES20.GL_FRAGMENT_SHADER, fragSrc)

        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vert)
        GLES20.glAttachShader(prog, frag)
        GLES20.glLinkProgram(prog)
        // Shaders are now baked into the program — delete the intermediate objects
        GLES20.glDeleteShader(vert)
        GLES20.glDeleteShader(frag)

        val status = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == GLES20.GL_FALSE) {
            val log = GLES20.glGetProgramInfoLog(prog)
            GLES20.glDeleteProgram(prog)
            error("Program link failed: $log")
        }

        programId = prog
    }

    fun use() = GLES20.glUseProgram(programId)

    fun getAttribLocation(name: String): Int  = GLES20.glGetAttribLocation(programId,  name)
    fun getUniformLocation(name: String): Int = GLES20.glGetUniformLocation(programId, name)

    fun release() = GLES20.glDeleteProgram(programId)

    // ── Private ────────────────────────────────────────────────────────────────

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == GLES20.GL_FALSE) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error("Shader compile failed (type=$type): $log")
        }
        return shader
    }
}
