package com.vibrdrome.app.visualizer

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class VisualizerRenderer : GLSurfaceView.Renderer {

    private var program = 0
    private var startTime = 0L
    private var width = 1f
    private var height = 1f

    // Audio data
    @Volatile var energy = 0f
    @Volatile var bass = 0f
    @Volatile var mid = 0f
    @Volatile var treble = 0f
    @Volatile var waveformData = ByteArray(0)

    private var currentPreset: ShaderPreset = ShaderPresets.plasma
    private var needsShaderReload = false
    private var waveformTexture = 0

    // Fullscreen quad
    private val quadVertices: FloatBuffer = ByteBuffer
        .allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            position(0)
        }

    private val vertexShader = """
        attribute vec2 aPosition;
        void main() {
            gl_Position = vec4(aPosition, 0.0, 1.0);
        }
    """

    fun setPreset(preset: ShaderPreset) {
        currentPreset = preset
        needsShaderReload = true
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        startTime = System.currentTimeMillis()
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        loadShader()
        createWaveformTexture()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w.toFloat()
        height = h.toFloat()
        GLES20.glViewport(0, 0, w, h)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (needsShaderReload) {
            loadShader()
            needsShaderReload = false
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (program == 0) return

        GLES20.glUseProgram(program)

        // Time
        val time = (System.currentTimeMillis() - startTime) / 1000f
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uTime"), time)

        // Audio uniforms
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uEnergy"), energy)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uBass"), bass)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uMid"), mid)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uTreble"), treble)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uResolution"), width, height)

        // Waveform texture
        updateWaveformTexture()
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, waveformTexture)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uWaveform"), 0)

        // Draw quad
        val posLoc = GLES20.glGetAttribLocation(program, "aPosition")
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 0, quadVertices)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(posLoc)
    }

    private fun loadShader() {
        if (program != 0) GLES20.glDeleteProgram(program)

        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexShader)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, currentPreset.fragmentShader)

        if (vs == 0 || fs == 0) {
            program = 0
            return
        }

        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }

        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    private fun createWaveformTexture() {
        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        waveformTexture = texIds[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, waveformTexture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private fun updateWaveformTexture() {
        val data = waveformData
        if (data.isEmpty()) return
        val buffer = ByteBuffer.allocateDirect(data.size).put(data)
        buffer.position(0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, waveformTexture)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE,
            data.size, 1, 0,
            GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, buffer,
        )
    }
}
