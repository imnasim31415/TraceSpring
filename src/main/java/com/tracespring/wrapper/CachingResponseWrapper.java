package com.tracespring.wrapper;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.*;

/**
 * Intercepts the response body so it can be inspected before being sent to the client.
 * Call copyBodyToResponse() at the end of the filter chain to flush the buffer.
 */
public class CachingResponseWrapper extends HttpServletResponseWrapper {

    private final ByteArrayOutputStream cachedContent = new ByteArrayOutputStream();
    private final PrintWriter writer;
    private final ServletOutputStream outputStream;

    public CachingResponseWrapper(HttpServletResponse response) {
        super(response);
        this.outputStream = new ServletOutputStream() {
            @Override public boolean isReady()                              { return true; }
            @Override public void setWriteListener(WriteListener listener)  {}
            @Override public void write(int b)                              { cachedContent.write(b); }
            @Override public void write(byte[] b, int off, int len)         { cachedContent.write(b, off, len); }
        };
        this.writer = new PrintWriter(new OutputStreamWriter(cachedContent));
    }

    @Override public ServletOutputStream getOutputStream() { return outputStream; }
    @Override public PrintWriter         getWriter()       { return writer; }

    /** Flush cached body to the actual response wire. Must be called once at filter exit. */
    public void copyBodyToResponse() throws IOException {
        writer.flush();
        getResponse().getOutputStream().write(cachedContent.toByteArray());
    }

    public String getBodyAsString() {
        writer.flush();
        return cachedContent.toString();
    }
}
