package com.gabriel.hson.stream;

import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.Objects;

//reading gson program for understand streaming API
//this file will be Refactored
public class JsonReader implements Cloneable {
    private static final int PEEKED_NONE = 0;
    private static final int PEEKED_BEGIN_OBJECT = 1;
    private static final int PEEKED_END_OBJECT = 2;
    private static final int PEEKED_BEGIN_ARRAY =3;
    private static final int PEEKED_END_ARRAY = 4;
    private static final int PEEKED_TRUE = 5;
    private static final int PEEKED_FALSE = 6;
    private static final int PEEKED_NULL = 7;
    private static final int PEEKED_SINGLE_QUOTED = 8;
    private static final int PEEKED_DOUBLE_QUOTED = 9;
    private static final int PEEKED_UNQUOTED = 10;
    private static final int PEEKED_BUFFERED = 11;
    private static final int PEEKED_SINGLE_QUOTED_NAME = 12;
    private static final int PEEKED_DOUBLE_QUOTED_NAME = 13;
    private static final int PEEKED_UNQUOTED_NAME = 14;
    private static final int PEEKED_LONG = 15;
    private static final int PEEKED_NUMBER = 16;
    private static final int PEEKED_EOF = 17;
    private int[] stack = new int[32];
    private int stackSize = 0;
    {
        stack[stackSize++] =JsonScope.EMPTY_DOCUMENT;
    }
    private String[] pathNames = new String[32];
    private int[] pathIndices = new int[32];
    private final int BUFFER_SIZE =1024;
    private final char[] buffer = new char[BUFFER_SIZE];
    private int pos = 0;
    private int limit = 0;
    private int lineStart = 0;
    private int lineNumber = 0;

    private final Reader in;
    int peeked = PEEKED_NONE;

    public JsonReader(Reader in) {
        this.in = Objects.requireNonNull(in, "input is null");
    }

    public void beginArray() throws Exception {
        int p =peeked;
        if (p == PEEKED_NONE) {
            p = doPeek();
        }
        if (p == PEEKED_BEGIN_ARRAY) {
            push(JsonScope.EMPTY_ARRAY);
            pathIndices[stackSize - 1] = 0;
            peeked = PEEKED_NONE;
        } else{
            throw unexpectedTokenError("BEGIN_ARRAY");
        }
    }
    public void endArray() throws Exception {
        int p = peeked;
        if (p == PEEKED_NONE) {
            doPeek();
        }
        if (p == PEEKED_END_ARRAY) {
            stackSize--;
            pathIndices[stackSize - 1]++;
            peeked = PEEKED_NONE;
        } else {
            throw unexpectedTokenError("END_ARRAY");
        }
    }
    public void beginObject() throws Exception {
        int p = peeked;
        if (p == PEEKED_NONE) {
            p = doPeek();
        }
        if (p == PEEKED_BEGIN_OBJECT) {
            push(JsonScope.EMPTY_OBJECT);
            peeked = PEEKED_NONE;
        } else {
            throw unexpectedTokenError("BEGIN_OBJECT");
        }
    }
    public void endObject() throws Exception {
        int p = peeked;
        if (p == PEEKED_NONE) {
            p = doPeek();
        }
        if (p == PEEKED_END_OBJECT) {
            stackSize--;
            pathNames[stackSize] = null;
            pathIndices[stackSize - 1]++;
            peeked = PEEKED_NONE;
        } else {
            throw unexpectedTokenError("END_OBJECT");
        }
    }

    private Exception unexpectedTokenError(String beginArray) {
        //TODO
        return new Exception();
    }

    private void push(int newTop) {
        if (stackSize == stack.length) {
            int newLength =stackSize * 2;
            stack = Arrays.copyOf(stack, newLength);
            pathIndices = Arrays.copyOf(pathIndices, newLength);
            pathNames = Arrays.copyOf(pathNames, newLength);
        }
        stack[stackSize++] = newTop;
    }

    public boolean hasNext() throws Exception {
        int p = peeked;
        if (p == PEEKED_NONE) {
            p = doPeek();
        }
        return p != PEEKED_END_OBJECT && p != PEEKED_END_ARRAY && p != PEEKED_EOF;
    }

    public int doPeek() throws IOException {
        int peekStack = stack[stackSize - 1];
        if (peekStack == JsonScope.EMPTY_ARRAY) {
            stack[stackSize - 1] = JsonScope.NONEMPTY_ARRAY;
        } else if (peekStack == JsonScope.NONEMPTY_ARRAY) {
            int c = nextNonWhitespace(true);
            switch (c) {
                case ']':
                    return peeked = PEEKED_END_ARRAY;
                case ';':
                    checkLenient();
                case ',':
                    break;
                default:
                    throw syntaxError("error");
            }
        }
        return 0;
    }

    public String nextName() throws IOException {
        int p =peeked;
        if (p == PEEKED_NONE) {
            p = doPeek();
        }
        String result = null;
        if (p == PEEKED_UNQUOTED_NAME) {
            result = nextUnquotedName();
        }
        return result;
    }

    public boolean nextBoolean() throws Exception {
        int p = peeked;
        if (p == PEEKED_NONE) {
            p = doPeek();
        }
        if (p == PEEKED_TRUE) {
            peeked = PEEKED_NONE;
            pathIndices[stackSize - 1]++;
            return true;
        } else if (p == PEEKED_FALSE) {
            peeked = PEEKED_NONE;
            pathIndices[stackSize - 1]++;
            return false;
        }
        throw unexpectedTokenError("a boolean");
    }
    public void nextNull() throws Exception {
        int p = peeked;
        if (p == PEEKED_NONE) {
            p = doPeek();
        }
        if (p == PEEKED_NULL) {
            peeked = PEEKED_NONE;
            pathIndices[stackSize - 1]++;
        } else {
            throw unexpectedTokenError("null");
        }
    }

    @SuppressWarnings("fallthrough")
    private String nextUnquotedName() throws IOException {
        StringBuilder builder = null;
        int i = 0;
        findNonLiteralCharacter:
        while(true) {
            for (;pos + i < limit;i++) {
                switch(buffer[pos + i]) {
                    case '/':
                    case '\\':
                    case ';':
                    case '#':
                    case '=':
                        checkLenient(); // fall-through
                    case '{':
                    case '}':
                    case '[':
                    case ']':
                    case ':':
                    case ',':
                    case ' ':
                    case '\t':
                    case '\f':
                    case '\r':
                    case '\n':
                        break findNonLiteralCharacter;
                }
            }
            if (i < buffer.length) {
                if (fillBuffer(i + 1)) {
                    continue;
                } else {
                    break;
                }
            }
            if (builder == null) {
                builder = new StringBuilder(Math.max(i,16));
            }
            builder.append(buffer, pos, i);
            pos += i;
            i = 0;
            if (!fillBuffer(1)) {
                break;
            }
        }
        String result = (null == builder) ? new String(buffer, pos, i) : builder.append(buffer, pos, i).toString();
        pos += i;
        return result;
    }

    private IOException syntaxError(String error) {
        //TODO
        return new IOException();
    }

    private void checkLenient() {
    }

    private int nextNonWhitespace(boolean throwOnEof) throws IOException {
        char[] buffer = this.buffer;
        int p = this.pos;
        int l = this.limit;
        while(true) {
            if (p == l) {
                pos = p;
                if (!fillBuffer(1)) {
                    break;
                }
                p = pos;
                l = limit;
            }
            int c = buffer[p++];
            if (c =='\n') {
                lineNumber++;
                lineStart = p;
                continue;
            } else if (c == ' ' || c== '\r' || c == '\t') {
                continue;
            }
            if (c == '/') {
                pos = p;
                pos--;
                boolean charsLoaded =fillBuffer(2);
                pos++;
                if (!charsLoaded) {
                    return c;
                }
            }
            checkLenient();
            char peek = buffer[pos];
            switch (peek) {
                case '*' -> {
                    pos++;
                    if (!skipTo("*/")) {
                        throw syntaxError("Unterminated comment");
                    }
                    p = pos + 2;
                    l = limit;
                    continue;
                }
                case '/' -> {
                    pos++;
                    skipToEndOfLine();
                    p = pos;
                    l = limit;
                    continue;
                }
                default -> {
                    return c;
                }
            }

        }
        if (throwOnEof) {
            throw new IOException();
        }else {
            return -1;
        }
    }

    private boolean skipTo(String s) {
        int length = s.length();
        return false;
    }

    private void skipToEndOfLine() throws IOException {
        while (pos < limit || fillBuffer(1)) {
            char c =buffer[pos++];
            if (c == '\n') {
                lineNumber++;
                lineStart = pos;
                break;
            } else if (c == '\r') {
                break;
            }

        }
    }

    private boolean fillBuffer(int minimum) throws IOException {
        char[] buffer = this.buffer;
        lineStart -= pos;
        if (limit != pos) {
            limit -= pos;
            System.arraycopy(buffer,pos,buffer,0,limit);
        } else {
            limit = 0;
        }
        pos = 0;
        int total;
        while((total= in.read(buffer, limit, buffer.length - limit)) != -1) {
            limit += total;
            if (lineNumber == 0 && lineStart == 0 && limit > 0 && buffer[0] =='\ufeff') {
                pos++;
                lineStart++;
                minimum++;
            }
            if (limit >= minimum) {
                return true;
            }
        }
        return false;
    }

    @Override
    public JsonReader clone() {
        try {
            return (JsonReader) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
    private String getPath(boolean usePreviousPath) {
        StringBuilder result = new StringBuilder().append('$');
        for (int i = 0; i < stackSize; i++) {
            switch (stack[i]) {
                case JsonScope.EMPTY_ARRAY:
                case JsonScope.NONEMPTY_ARRAY:
                    int pathIndex = pathIndices[i];
                    if (usePreviousPath && pathIndex > 0 && i == stackSize - 1) {
                        pathIndex--;
                    }
                    result.append('[').append(pathIndex).append(']');
                    break;
                case JsonScope.EMPTY_OBJECT:
                case JsonScope.DANGLING_NAME:
                case JsonScope.NONEMPTY_OBJECT:
                    result.append('.');
                    if (pathNames[i] != null) {
                        result.append(pathNames[i]);
                    }
                    break;
                case JsonScope.NONEMPTY_DOCUMENT:
                case JsonScope.EMPTY_DOCUMENT:
                case JsonScope.CLOSED:
                    break;
            }
        }
        return result.toString();
    }
}
