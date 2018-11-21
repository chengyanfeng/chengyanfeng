package number400;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import mongodb.Mongodbjdbc;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.ExcelUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Number400 {
    private static final Logger logger = LoggerFactory
            .getLogger(Mongodbjdbc.class);
    public static void main(String[] args) {
        MongoCursor<Document> iterator = null;
        try {
            List<Document> pipeline = new ArrayList<>();

//            筛选条件
            Document match = new Document("beginTime", new Document("$gte", "1464710400"))
                    .append("endTime", new Document("$lte", "1467302399"));
            pipeline.add(new Document("$match", match));

//            返回字段
            Document project = new Document("account", 1).append("beginTime", 1)
                    .append("endTime", 1)
                    .append("isLocal", 1)
                    .append("feeStrategy", 1)
                    .append("seconds", 1)
                    .append("seconds6", 1)
                    .append("minutes", 1)
                    .append("did", 1);
            pipeline.add(new Document("$project", project));

//            分组条件
            Document groupBy = new Document("_id", new Document("did", "$did").append("account", "$account").append("feeStrategy", "$feeStrategy"))
                    .append("totalCount", new Document("$sum", 1))
                    .append("totalMinutes", new Document("$sum", "$minutes"))
                    .append("totalSeconds", new Document("$sum", "$seconds"))
                    .append("totalSeconds6", new Document("$sum", "$seconds6"));
            pipeline.add(new Document("$group", groupBy));


            MongoCollection<Document> collection = Mongodbjdbc.MongoConnet().getCollection("bill_cdr_query_cc");
            iterator = collection.aggregate(pipeline).allowDiskUse(true).iterator();

//            创建工作簿和表
            SXSSFWorkbook wb = ExcelUtil.INSTANCE.returnWorkBookGivenFileHandle("E:\\账户统计数据\\test111.xlsx", "Sheet1");
            if (null == wb) {
                return;
            }

//            将列头添加至表
            SXSSFSheet sheet = ExcelUtil.INSTANCE.returnSheetFromWorkBook(wb);
            Map<Integer, String> headers = new HashMap<>();
            headers.put(0, "账户编号");
            headers.put(1, "400号码");
            headers.put(2, "条数");
            headers.put(3, "计费分钟");
            headers.put(4, "计费6秒数");
            headers.put(5, "计费秒数");
            headers.put(6, "计费费用");
            ExcelUtil.INSTANCE.insertRows(sheet, ExcelUtil.INSTANCE.getNextRowNum(), headers);

            while (iterator.hasNext()) {
                Document document = iterator.next();
                String account = document.get("_id", Document.class).getString("account");
                String num400 = document.get("_id", Document.class).getString("did");
                String totalCount = String.valueOf(document.getInteger("totalCount"));
                String totalMinutes = String.valueOf(document.getLong("totalMinutes"));
                String totalSeconds6 = String.valueOf(document.getLong("totalSeconds6"));
                String totalSeconds = String.valueOf(document.getLong("totalSeconds"));

                Map<Integer, String> rowCells = new HashMap<>();
                rowCells.put(0, account);
                rowCells.put(1, num400);
                rowCells.put(2, totalCount);
                rowCells.put(3, totalMinutes);
                rowCells.put(4, totalSeconds6);
                rowCells.put(5, totalSeconds);
//                rowCells.put(6, account);

                ExcelUtil.INSTANCE.insertRows(sheet, ExcelUtil.INSTANCE.getNextRowNum(), rowCells);
                logger.debug("---------------insert success--------------",account);
            }
            ExcelUtil.INSTANCE.saveExcelAndReset(wb, ExcelUtil.INSTANCE.getFilePath());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (null != iterator) {
                iterator.close();
            }
        }
    }
}
