package org.apache.lucene.jiangjibo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.github.maltalex.ineter.base.IPv4Address;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DocValuesType;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.SortField.Type;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.junit.Before;
import org.junit.Test;

/**
 * @author wb-jjb318191
 * @create 2020-09-09 11:22
 */
public class Ipv4IndexWriterTest {

    private IndexWriter indexWriter;

    private FieldType keywordType;

    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(5);

    Lock lock = new ReentrantLock();
    private Condition condition = lock.newCondition();

    @Before
    public void init() throws IOException {
        indexWriter = buildIndexWriter();
        keywordType = keywordFieldType();
    }

    @Test
    public void indexDocumentConcurrent() throws InterruptedException {
        Callable runnable = () -> {
            writeIpv4Data();
            return null;
        };
        //executorService.schedule(runnable, 1, TimeUnit.MICROSECONDS);
        //executorService.schedule(runnable, 1, TimeUnit.MICROSECONDS);
        //executorService.schedule(runnable, 1, TimeUnit.MICROSECONDS);
        //executorService.schedule(runnable, 1, TimeUnit.MICROSECONDS);

        executorService.submit(runnable);
        executorService.submit(runnable);

        while (true) {
            Thread.sleep(1);
            lock.lock();
            try {
                indexWriter.flush();
                condition.signalAll();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        }

    }

    @Test
    public void writeIpv4Data() throws IOException, InterruptedException {
        File txt = new File("C:\\Users\\wb-jjb318191\\Desktop\\ipv4.txt");
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(txt)));
        String line;
        int total = 0;
        List<Map<String, Object>> values = new ArrayList<>(1000);

        FieldType numberType = numberFieldType();

        while ((line = reader.readLine()) != null) {
            int index = 0;
            List<String> splits = Splitter.on(",").splitToList(line);
            Map<String, Object> record = new HashMap<>();
            record.put("start", IPv4Address.of(splits.get(index++)).toLong());
            record.put("end", IPv4Address.of(splits.get(index++)).toLong());
            record.put("country", splits.get(index++));
            record.put("province", splits.get(index++));
            record.put("city", splits.get(index++));
            record.put("region", splits.get(index++));
            record.put("isp", splits.get(index++));
            record.put("address",
                Joiner.on(" ").join(record.get("country").toString(), record.get("province"), record.get("city"), record.get("region"), record.get("isp")));
            values.add(record);

            Document document = new Document();
            document.add(new NumericDocValuesField("start", (Long)record.get("start")));
            document.add(new NumericDocValuesField("end", (Long)record.get("end")));
            //document.add(new IntRange("range", new int[] {(int)record.get("start")}, new int[] {(int)record.get("end")}));
            document.add(new Field("country", record.get("country").toString(), keywordType));
            document.add(new Field("province", record.get("province").toString(), keywordType));
            document.add(new Field("city", record.get("city").toString(), keywordType));
            document.add(new Field("region", record.get("region").toString(), keywordType));
            document.add(new Field("isp", record.get("isp").toString(), keywordType));
            document.add(new Field("address", record.get("address").toString(), TextField.TYPE_STORED));
            indexWriter.addDocument(document);
            total++;
            if (total % 10000 == 0) {
                indexWriter.flush();
            }
            if(total == 50000){
                indexWriter.close();
            }
        }
    }

    private IndexWriter buildIndexWriter() throws IOException {
        // Store the index in memory:
        // To store an index on disk, use this instead:
        Directory directory = FSDirectory.open(Paths.get("D:\\lucene-data"));
        IndexWriterConfig config = new IndexWriterConfig(new WhitespaceAnalyzer());

        // 设置document在segment里的顺序, 默认是docId, 如果设置成某个Field或者多个Field, 则在检索时能够实现EarlyTerminate
        Sort sort = new Sort(new SortField("timestamp", Type.LONG, true), new SortField("age", Type.INT, false));
        config.setIndexSort(sort);

        IndexWriter indexWriter = new IndexWriter(directory, config);

        return indexWriter;
    }

    private FieldType keywordFieldType() {
        FieldType fieldType = new FieldType();
        fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
        fieldType.setTokenized(false);
        fieldType.setStored(true);
        fieldType.freeze();
        return fieldType;
    }

    private FieldType numberFieldType() {
        FieldType fieldType = new FieldType();
        fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
        fieldType.setTokenized(false);
        fieldType.setStored(true);
        fieldType.setDocValuesType(DocValuesType.NUMERIC);
        fieldType.freeze();
        return fieldType;
    }

    @Test
    public void testMerge() throws IOException {
        IndexWriter indexWriter = buildIndexWriter();
        indexWriter.forceMerge(2);
        indexWriter.close();
    }

    @Test
    public void testRefresh() throws IOException {
        IndexWriter indexWriter = buildIndexWriter();
        // 相当于es的refresh,每次执行生成一个segment, 也就是一系列的文件
        indexWriter.flush();
        // 相当于es的flush
        indexWriter.commit();
    }

    @Test
    public void deleteDocument() throws IOException {
        IndexWriter indexWriter = buildIndexWriter();
        long l = indexWriter.deleteDocuments(new Term("city", "上海市"));
        System.out.println(l);
        indexWriter.flush();
        // close时会触发一次merge
        indexWriter.close();
    }

}
