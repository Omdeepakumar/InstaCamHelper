package com.manus.instacam;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class FilterManager {

    private static final String VERTEX_SHADER =
            "attribute vec4 vPosition;\n" +
            "attribute vec2 vTexCoord;\n" +
            "varying vec2 texCoord;\n" +
            "void main() {\n" +
            "  gl_Position = vPosition;\n" +
            "  texCoord = vTexCoord;\n" +
            "}";

    private static final String FRAGMENT_SHADER_NORMAL =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "varying vec2 texCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "void main() {\n" +
            "  gl_FragColor = texture2D(sTexture, texCoord);\n" +
            "}";

    private static final String FRAGMENT_SHADER_GRAYSCALE =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "varying vec2 texCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "void main() {\n" +
            "  vec4 color = texture2D(sTexture, texCoord);\n" +
            "  float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));\n" +
            "  gl_FragColor = vec4(vec3(gray), color.a);\n" +
            "}";

    private static final String FRAGMENT_SHADER_SEPIA =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "varying vec2 texCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "void main() {\n" +
            "  vec4 color = texture2D(sTexture, texCoord);\n" +
            "  float r = color.r * 0.393 + color.g * 0.769 + color.b * 0.189;\n" +
            "  float g = color.r * 0.349 + color.g * 0.686 + color.b * 0.168;\n" +
            "  float b = color.r * 0.272 + color.g * 0.534 + color.b * 0.131;\n" +
            "  gl_FragColor = vec4(r, g, b, color.a);\n" +
            "}";

    private FloatBuffer vertexBuffer;
    private FloatBuffer texCoordBuffer;
    private int program;
    private int currentFilter = 0;

    private static final float[] VERTICES = {
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f
    };

    private static final float[] TEX_COORDS = {
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f
    };

    public void init() {
        ByteBuffer bb = ByteBuffer.allocateDirect(VERTICES.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(VERTICES);
        vertexBuffer.position(0);

        ByteBuffer tb = ByteBuffer.allocateDirect(TEX_COORDS.length * 4);
        tb.order(ByteOrder.nativeOrder());
        texCoordBuffer = tb.asFloatBuffer();
        texCoordBuffer.put(TEX_COORDS);
        texCoordBuffer.position(0);

        updateProgram(FRAGMENT_SHADER_NORMAL);
    }

    private void updateProgram(String fragmentShaderCode) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);
        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);
    }

    public void setFilter(int filterType) {
        currentFilter = filterType;
        switch (filterType) {
            case 1: updateProgram(FRAGMENT_SHADER_GRAYSCALE); break;
            case 2: updateProgram(FRAGMENT_SHADER_SEPIA); break;
            default: updateProgram(FRAGMENT_SHADER_NORMAL); break;
        }
    }

    public void draw(int textureId) {
        GLES20.glUseProgram(program);
        int positionHandle = GLES20.glGetAttribLocation(program, "vPosition");
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);

        int texCoordHandle = GLES20.glGetAttribLocation(program, "vTexCoord");
        GLES20.glEnableVertexAttribArray(texCoordHandle);
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 8, texCoordBuffer);

        int textureHandle = GLES20.glGetUniformLocation(program, "sTexture");
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES20.glUniform1i(textureHandle, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(texCoordHandle);
    }

    public int createTexture() {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0]);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        return textures[0];
    }

    private int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }
}
