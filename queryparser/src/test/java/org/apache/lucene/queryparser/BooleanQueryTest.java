package org.apache.lucene.queryparser;

import java.io.IOException;
import java.nio.file.Paths;

import com.github.maltalex.ineter.base.IPv4Address;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DocValuesTermsQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.junit.Before;
import org.junit.Test;

/**
 * @author wb-jjb318191
 * @create 2020-09-22 10:07
 */
public class BooleanQueryTest {

    private IndexSearcher indexSearcher;

    @Before
    public void init() throws IOException {
        Directory directory = FSDirectory.open(Paths.get("D:\\lucene-data"));
        DirectoryReader ireader = DirectoryReader.open(directory);
        indexSearcher = new IndexSearcher(ireader);
    }

    @Test
    public void buildBooleanQuery() throws IOException {
        BytesRef lowerTerm = new BytesRef(IPv4Address.of("11.12.0.0").toBigEndianArray());
        BytesRef upperTerm = new BytesRef(IPv4Address.of("11.158.0.0").toBigEndianArray());
        TermRangeQuery rangeQuery = new TermRangeQuery("start", lowerTerm, upperTerm, true, true);

        TermQuery termQuery = new TermQuery(new Term("country", "中国"));

        BooleanQuery booleanQuery = new BooleanQuery.Builder()
            .add(rangeQuery, Occur.MUST)
            //.add(termQuery, Occur.FILTER)
            .setMinimumNumberShouldMatch(1).build();

        TopDocs topDocs = indexSearcher.search(booleanQuery, 10);

        System.out.println(topDocs.totalHits);

        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            System.out.println(scoreDoc.doc);
        }

    }

}
