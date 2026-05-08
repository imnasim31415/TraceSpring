package com.tracespring.wrapper;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.*;

/**
 * Caches the request body so it can be read multiple times.
 * Spring MVC's default InputStream is single-read; without this wrapper
 * body parsing in the controller would drain the stream before tracing reads it.
 */
public class CachingRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    public CachingRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        this.cachedBody = request.getInputStream().readAllBytes();
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream bais = new ByteArrayInputStream(cachedBody);
        return new ServletInputStream() {
            @Override public boolean isFinished()                         { return bais.available() == 0; }
            @Override public boolean isReady()                           { return true; }
            @Override public void setReadListener(ReadListener listener) {}
            @Override public int read()                                  { return bais.read(); }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream()));
    }

    public String getBodyAsString() {
        return new String(cachedBody);
    }
}
