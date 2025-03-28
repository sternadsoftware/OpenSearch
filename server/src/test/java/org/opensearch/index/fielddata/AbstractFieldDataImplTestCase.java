/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.index.fielddata;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.SortedNumericSortField;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.util.BytesRef;
import org.opensearch.core.common.Strings;
import org.opensearch.search.MultiValueMode;

import java.io.IOException;
import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

public abstract class AbstractFieldDataImplTestCase extends AbstractFieldDataTestCase {

    protected String one() {
        return "1";
    }

    protected String two() {
        return "2";
    }

    protected String three() {
        return "3";
    }

    protected String four() {
        return "4";
    }

    protected String toString(Object value) {
        if (value instanceof BytesRef) {
            return ((BytesRef) value).utf8ToString();
        }
        return value.toString();
    }

    protected abstract void fillSingleValueAllSet() throws Exception;

    protected abstract void add2SingleValuedDocumentsAndDeleteOneOfThem() throws Exception;

    protected long minRamBytesUsed() {
        // minimum number of bytes that this fielddata instance is expected to require
        return 1;
    }

    public void testDeletedDocs() throws Exception {
        add2SingleValuedDocumentsAndDeleteOneOfThem();
        IndexFieldData<?> indexFieldData = getForField("value");
        List<LeafReaderContext> readerContexts = refreshReader();
        for (LeafReaderContext readerContext : readerContexts) {
            LeafFieldData fieldData = indexFieldData.load(readerContext);
            SortedBinaryDocValues values = fieldData.getBytesValues();
            for (int i = 0; i < readerContext.reader().maxDoc(); ++i) {
                assertTrue(values.advanceExact(i));
                assertThat(values.docValueCount(), greaterThanOrEqualTo(1));
            }
        }
    }

    public void testSingleValueAllSet() throws Exception {
        fillSingleValueAllSet();
        IndexFieldData<?> indexFieldData = getForField("value");
        List<LeafReaderContext> readerContexts = refreshReader();
        for (LeafReaderContext readerContext : readerContexts) {
            LeafFieldData fieldData = indexFieldData.load(readerContext);
            assertThat(fieldData.ramBytesUsed(), greaterThanOrEqualTo(minRamBytesUsed()));

            SortedBinaryDocValues bytesValues = fieldData.getBytesValues();

            assertTrue(bytesValues.advanceExact(0));
            assertThat(bytesValues.docValueCount(), equalTo(1));
            assertThat(bytesValues.nextValue(), equalTo(new BytesRef(two())));
            assertTrue(bytesValues.advanceExact(1));
            assertThat(bytesValues.docValueCount(), equalTo(1));
            assertThat(bytesValues.nextValue(), equalTo(new BytesRef(one())));
            assertTrue(bytesValues.advanceExact(2));
            assertThat(bytesValues.docValueCount(), equalTo(1));
            assertThat(bytesValues.nextValue(), equalTo(new BytesRef(three())));

            bytesValues = fieldData.getBytesValues();
            assertValues(bytesValues, 0, two());
            assertValues(bytesValues, 1, one());
            assertValues(bytesValues, 2, three());

            IndexSearcher searcher = new IndexSearcher(readerContext.reader());
            TopFieldDocs topDocs;
            SortField sortField = indexFieldData.sortField(null, MultiValueMode.MIN, null, false);
            topDocs = searcher.search(new MatchAllDocsQuery(), 10, new Sort(sortField));
            assertThat(topDocs.totalHits.value(), equalTo(3L));
            assertThat(topDocs.scoreDocs[0].doc, equalTo(1));
            assertThat(toString(((FieldDoc) topDocs.scoreDocs[0]).fields[0]), equalTo(one()));
            assertThat(topDocs.scoreDocs[1].doc, equalTo(0));
            assertThat(toString(((FieldDoc) topDocs.scoreDocs[1]).fields[0]), equalTo(two()));
            assertThat(topDocs.scoreDocs[2].doc, equalTo(2));
            assertThat(toString(((FieldDoc) topDocs.scoreDocs[2]).fields[0]), equalTo(three()));

            sortField = indexFieldData.sortField(null, MultiValueMode.MAX, null, true);
            topDocs = searcher.search(new MatchAllDocsQuery(), 10, new Sort(sortField));
            assertThat(topDocs.totalHits.value(), equalTo(3L));
            assertThat(topDocs.scoreDocs[0].doc, equalTo(2));
            assertThat(topDocs.scoreDocs[1].doc, equalTo(0));
            assertThat(topDocs.scoreDocs[2].doc, equalTo(1));
        }
    }

    public void testWideSortField() throws Exception {
        if (this instanceof NoOrdinalsStringFieldDataTests || this instanceof PagedBytesStringFieldDataTests) {
            return; // Numeric types are not supported there.
        }
        // integer to long widening should happen
        IndexFieldData<?> indexFieldData = getForField("int", "value");
        SortField sortField = indexFieldData.wideSortField(null, MultiValueMode.MIN, null, false);
        assertTrue(((SortedNumericSortField) sortField).getNumericType() == SortField.Type.LONG);

        // long to long no widening should happen
        indexFieldData = getForField("long", "value");
        sortField = indexFieldData.wideSortField(null, MultiValueMode.MIN, null, false);
        assertTrue(((SortedNumericSortField) sortField).getNumericType() == SortField.Type.LONG);

        // float to float no widening should happen
        indexFieldData = getForField("float", "value");
        sortField = indexFieldData.wideSortField(null, MultiValueMode.MIN, null, false);
        assertTrue(((SortedNumericSortField) sortField).getNumericType() == SortField.Type.FLOAT);

    }

    protected abstract void fillSingleValueWithMissing() throws Exception;

    public void assertValues(SortedBinaryDocValues values, int docId, BytesRef... actualValues) throws IOException {
        assertEquals(actualValues.length > 0, values.advanceExact(docId));
        if (actualValues.length > 0) {
            assertThat(values.docValueCount(), equalTo(actualValues.length));
            for (int i = 0; i < actualValues.length; i++) {
                assertThat(values.nextValue(), equalTo(actualValues[i]));
            }
        }
    }

    public void assertValues(SortedBinaryDocValues values, int docId, String... actualValues) throws IOException {
        assertEquals(actualValues.length > 0, values.advanceExact(docId));
        if (actualValues.length > 0) {
            assertThat(values.docValueCount(), equalTo(actualValues.length));
            for (int i = 0; i < actualValues.length; i++) {
                assertThat(values.nextValue(), equalTo(new BytesRef(actualValues[i])));
            }
        }
    }

    public void testSingleValueWithMissing() throws Exception {
        fillSingleValueWithMissing();
        IndexFieldData<?> indexFieldData = getForField("value");
        List<LeafReaderContext> readerContexts = refreshReader();
        for (LeafReaderContext readerContext : readerContexts) {
            LeafFieldData fieldData = indexFieldData.load(readerContext);
            assertThat(fieldData.ramBytesUsed(), greaterThanOrEqualTo(minRamBytesUsed()));

            SortedBinaryDocValues bytesValues = fieldData.getBytesValues();

            assertValues(bytesValues, 0, two());
            assertValues(bytesValues, 1, Strings.EMPTY_ARRAY);
            assertValues(bytesValues, 2, three());
        }
    }

    protected abstract void fillMultiValueAllSet() throws Exception;

    public void testMultiValueAllSet() throws Exception {
        fillMultiValueAllSet();
        // the segments are force merged to a single segment so that the sorted binary doc values can be asserted within a single segment.
        // Previously we used the SlowCompositeReaderWrapper but this is an unideal solution so force merging is a better idea.
        writer.forceMerge(1);
        IndexFieldData<?> indexFieldData = getForField("value");
        List<LeafReaderContext> readerContexts = refreshReader();
        for (LeafReaderContext readerContext : readerContexts) {
            LeafFieldData fieldData = indexFieldData.load(readerContext);
            assertThat(fieldData.ramBytesUsed(), greaterThanOrEqualTo(minRamBytesUsed()));

            SortedBinaryDocValues bytesValues = fieldData.getBytesValues();
            assertValues(bytesValues, 0, two(), four());
            assertValues(bytesValues, 1, one());
            assertValues(bytesValues, 2, three());

            IndexSearcher searcher = new IndexSearcher(DirectoryReader.open(writer));
            SortField sortField = indexFieldData.sortField(null, MultiValueMode.MIN, null, false);
            TopFieldDocs topDocs = searcher.search(new MatchAllDocsQuery(), 10, new Sort(sortField));
            assertThat(topDocs.totalHits.value(), equalTo(3L));
            assertThat(topDocs.scoreDocs.length, equalTo(3));
            assertThat(topDocs.scoreDocs[0].doc, equalTo(1));
            assertThat(topDocs.scoreDocs[1].doc, equalTo(0));
            assertThat(topDocs.scoreDocs[2].doc, equalTo(2));
            sortField = indexFieldData.sortField(null, MultiValueMode.MAX, null, true);
            topDocs = searcher.search(new MatchAllDocsQuery(), 10, new Sort(sortField));
            assertThat(topDocs.totalHits.value(), equalTo(3L));
            assertThat(topDocs.scoreDocs.length, equalTo(3));
            assertThat(topDocs.scoreDocs[0].doc, equalTo(0));
            assertThat(topDocs.scoreDocs[1].doc, equalTo(2));
            assertThat(topDocs.scoreDocs[2].doc, equalTo(1));
        }
    }

    protected abstract void fillMultiValueWithMissing() throws Exception;

    public void testMultiValueWithMissing() throws Exception {
        fillMultiValueWithMissing();
        IndexFieldData<?> indexFieldData = getForField("value");
        List<LeafReaderContext> readerContexts = refreshReader();
        for (LeafReaderContext readerContext : readerContexts) {
            LeafFieldData fieldData = indexFieldData.load(readerContext);
            assertThat(fieldData.ramBytesUsed(), greaterThanOrEqualTo(minRamBytesUsed()));

            SortedBinaryDocValues bytesValues = fieldData.getBytesValues();

            assertValues(bytesValues, 0, two(), four());
            assertValues(bytesValues, 1, Strings.EMPTY_ARRAY);
            assertValues(bytesValues, 2, three());
        }
    }

    public void testMissingValueForAll() throws Exception {
        fillAllMissing();
        IndexFieldData<?> indexFieldData = getForField("value");
        List<LeafReaderContext> readerContexts = refreshReader();
        for (LeafReaderContext readerContext : readerContexts) {
            LeafFieldData fieldData = indexFieldData.load(readerContext);
            // Some impls (FST) return size 0 and some (PagedBytes) do take size in the case no actual data is loaded
            assertThat(fieldData.ramBytesUsed(), greaterThanOrEqualTo(0L));

            SortedBinaryDocValues bytesValues = fieldData.getBytesValues();

            assertValues(bytesValues, 0, Strings.EMPTY_ARRAY);
            assertValues(bytesValues, 1, Strings.EMPTY_ARRAY);
            assertValues(bytesValues, 2, Strings.EMPTY_ARRAY);
            SortedBinaryDocValues hashedBytesValues = fieldData.getBytesValues();

            assertValues(hashedBytesValues, 0, Strings.EMPTY_ARRAY);
            assertValues(hashedBytesValues, 1, Strings.EMPTY_ARRAY);
            assertValues(hashedBytesValues, 2, Strings.EMPTY_ARRAY);
        }
    }

    protected abstract void fillAllMissing() throws Exception;

    public void testSortMultiValuesFields() throws Exception {
        fillExtendedMvSet();
        IndexFieldData<?> indexFieldData = getForField("value");

        IndexSearcher searcher = new IndexSearcher(DirectoryReader.open(writer));
        SortField sortField = indexFieldData.sortField(null, MultiValueMode.MIN, null, false);
        TopFieldDocs topDocs = searcher.search(new MatchAllDocsQuery(), 10, new Sort(sortField));
        assertThat(topDocs.totalHits.value(), equalTo(8L));
        assertThat(topDocs.scoreDocs.length, equalTo(8));
        assertThat(topDocs.scoreDocs[0].doc, equalTo(7));
        assertThat(((BytesRef) ((FieldDoc) topDocs.scoreDocs[0]).fields[0]).utf8ToString(), equalTo("!08"));
        assertThat(topDocs.scoreDocs[1].doc, equalTo(0));
        assertThat(((BytesRef) ((FieldDoc) topDocs.scoreDocs[1]).fields[0]).utf8ToString(), equalTo("02"));
        assertThat(topDocs.scoreDocs[2].doc, equalTo(2));
        assertThat(((BytesRef) ((FieldDoc) topDocs.scoreDocs[2]).fields[0]).utf8ToString(), equalTo("03"));
        assertThat(topDocs.scoreDocs[3].doc, equalTo(3));
        assertThat(((BytesRef) ((FieldDoc) topDocs.scoreDocs[3]).fields[0]).utf8ToString(), equalTo("04"));
        assertThat(topDocs.scoreDocs[4].doc, equalTo(4));
        assertThat(((BytesRef) ((FieldDoc) topDocs.scoreDocs[4]).fields[0]).utf8ToString(), equalTo("06"));
        assertThat(topDocs.scoreDocs[5].doc, equalTo(6));
        assertThat(((BytesRef) ((FieldDoc) topDocs.scoreDocs[5]).fields[0]).utf8ToString(), equalTo("08"));
        assertThat(topDocs.scoreDocs[6].doc, equalTo(1));
        assertThat((BytesRef) ((FieldDoc) topDocs.scoreDocs[6]).fields[0], equalTo(null));
        assertThat(topDocs.scoreDocs[7].doc, equalTo(5));
        assertThat((BytesRef) ((FieldDoc) topDocs.scoreDocs[7]).fields[0], equalTo(null));

        sortField = indexFieldData.sortField(null, MultiValueMode.MAX, null, true);
        topDocs = searcher.search(new MatchAllDocsQuery(), 10, new Sort(sortField));
        assertThat(topDocs.totalHits.value(), equalTo(8L));
        assertThat(topDocs.scoreDocs.length, equalTo(8));
        assertThat(topDocs.scoreDocs[0].doc, equalTo(6));
        assertThat(((BytesRef) ((FieldDoc) topDocs.scoreDocs[0]).fields[0]).utf8ToString(), equalTo("10"));
        assertThat(topDocs.scoreDocs[1].doc, equalTo(4));
        assertThat(((BytesRef) ((FieldDoc) topDocs.scoreDocs[1]).fields[0]).utf8ToString(), equalTo("08"));
        assertThat(topDocs.scoreDocs[2].doc, equalTo(3));
        assertThat(((BytesRef) ((FieldDoc) topDocs.scoreDocs[2]).fields[0]).utf8ToString(), equalTo("06"));
        assertThat(topDocs.scoreDocs[3].doc, equalTo(0));
        assertThat(((BytesRef) ((FieldDoc) topDocs.scoreDocs[3]).fields[0]).utf8ToString(), equalTo("04"));
        assertThat(topDocs.scoreDocs[4].doc, equalTo(2));
        assertThat(((BytesRef) ((FieldDoc) topDocs.scoreDocs[4]).fields[0]).utf8ToString(), equalTo("03"));
        assertThat(topDocs.scoreDocs[5].doc, equalTo(7));
        assertThat(((BytesRef) ((FieldDoc) topDocs.scoreDocs[5]).fields[0]).utf8ToString(), equalTo("!10"));
        assertThat(topDocs.scoreDocs[6].doc, equalTo(1));
        assertThat(((FieldDoc) topDocs.scoreDocs[6]).fields[0], equalTo(null));
        assertThat(topDocs.scoreDocs[7].doc, equalTo(5));
        assertThat(((FieldDoc) topDocs.scoreDocs[7]).fields[0], equalTo(null));
    }

    protected abstract void fillExtendedMvSet() throws Exception;
}
