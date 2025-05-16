package com.github.einrobin.ocr.process;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface IOProcessListener {

    InputStream openInput() throws IOException;

    OutputStream openOutput() throws IOException;

    void onSuccess() throws IOException;
}
