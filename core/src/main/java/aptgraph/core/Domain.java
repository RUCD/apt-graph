/*
 * The MIT License
 *
 * Copyright 2016 Thibault Debatty & Thomas Gilon.
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
package aptgraph.core;

import java.util.LinkedList;

/**
 * Domain object.
 *
 * @author Thibault Debatty
 * @author Thomas Gilon
 */
public class Domain extends LinkedList<Request> {

    private String name = "";

    /**
     * Domain name.
     *
     * @return String : Domain name
     */
    public final String getName() {
        return name;
    }

    /**
     * Set domain name.
     *
     * @param name Domain name
     */
    public final void setName(final String name) {
        this.name = name;
    }

    /**
     * Merge domains with the same name but different requests.
     *
     * @param dom Domain to merge with the Domain object
     * @return dom_out : Domain after merge
     */
    public final Domain merge(final Domain dom) {
        Domain dom_out = (Domain) this.clone();
        if (this.name.equals(dom.name)) {
            for (Request req : dom) {
                if (!this.contains(req)) {
                    dom_out.add(req);
                }
            }
        }
        return dom_out;
    }

    /**
     * Compare two domains (size, name and requests).
     *
     * @param obj Domain
     * @return boolean : True if two Domains are identical
     */
    public final boolean deepEquals(final Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Domain dom = (Domain) obj;
        boolean output = false;
        if (this.getName().equals(dom.getName())
                && this.size() == dom.size()) {
            for (Request req : this) {
                if (dom.contains(req)) {
                    output = true;
                } else {
                    return false;
                }
            }
        }
        return output;
    }

    /**
     * Return domain name.
     *
     * @return String : Domain name
     */
    @Override
    public final String toString() {
        return name;
    }
}
