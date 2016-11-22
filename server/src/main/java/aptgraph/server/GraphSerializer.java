/*
 * The MIT License
 *
 * Copyright 2016 Thibault Debatty.
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
import info.debatty.java.graphs.Graph;
import java.io.IOException;

/**
 *
 * @author Thibault Debatty
 */
public class GraphSerializer extends StdSerializer<Graph> {

    /**
     * Default.
     */
    public GraphSerializer() {
          this(null);
    }

    /**
     * Default.
     * @param type
     */
    public GraphSerializer(final Class<Graph> type) {
        super(type);
    }

    /**
     * Serialize a Graph.
     * @param graph
     * @param jgen
     * @param provider
     * @throws IOException
     */
    @Override
    public final void serialize(
            final Graph graph,
            final JsonGenerator jgen,
            final SerializerProvider provider) throws IOException {

        jgen.writeStartObject();
        jgen.writeObjectField("nodes", graph.getNodes());
        jgen.writeObjectField("neighbors", graph.getHashMap());
        jgen.writeEndObject();
    }

}
