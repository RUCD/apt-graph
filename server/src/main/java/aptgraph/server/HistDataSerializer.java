/*
 * The MIT License
 *
 * Copyright 2017 Thomas Gilon.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package aptgraph.server;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;

/**
 * HistData serialization definition file.
 *
 * @author Thomas Gilon
 */
public class HistDataSerializer extends StdSerializer<HistData> {

    /**
     * Default.
     */
    public HistDataSerializer() {
        this(null);
    }

    /**
     * Default.
     *
     * @param type Type
     */
    public HistDataSerializer(final Class<HistData> type) {
        super(type);
    }

    /**
     * Serialize HistData.
     *
     * @param hist_data Histogram data
     * @param jgen JSON Generator
     * @param provider Serializer Provider
     * @throws IOException If data can not be written
     */
    @Override
    public final void serialize(final HistData hist_data,
            final JsonGenerator jgen,
            final SerializerProvider provider) throws IOException {

        jgen.writeStartObject();
        jgen.writeStringField("array", hist_data.getArray());
        jgen.writeEndObject();

    }
}
