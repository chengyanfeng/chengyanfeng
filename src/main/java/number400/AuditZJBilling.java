package number400;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.bson.Document;
import util.ExcelUtil;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum AuditZJBilling {
    INSTANCE;

    /**
     * 计算总计数据
     *
     * @param startTime        开始时间
     * @param endTime          结束时间
     * @param filePath         excel文件路径
     * @param defaultSheetName 默认工作表名
     */
    public void calculateZJ(String startTime, String endTime, String filePath, String defaultSheetName) {
        MongoCursor<Document> iterator = null;
        try {
            iterator = this.queryData(startTime, endTime);
            this.exportData(filePath, defaultSheetName, iterator);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (null != iterator) {
                iterator.close();
            }
        }
    }


    /**
     * 查询数据
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 查询结果
     */
    private MongoCursor<Document> queryData(String startTime, String endTime) {
        List<Document> pipeline = new ArrayList<>();

//        筛选条件
        Document match = new Document("beginTime", new Document("$gte", startTime))
                .append("beginTime", new Document("$lte", endTime));
        pipeline.add(new Document("$match", match));

//        返回字段
        Document project = new Document("account", 1).append("beginTime", 1)
                .append("endTime", 1)
                .append("isLocal", 1)
                .append("feeStrategy", 1)
                .append("seconds", 1)
                .append("seconds6", 1)
                .append("minutes", 1)
                .append("did", 1)
                .append("type", 1);
        pipeline.add(new Document("$project", project));

//        分组条件
        Document groupBy = new Document("_id", new Document("did", "$did").append("account", "$account").append("feeStrategy", "$feeStrategy"))
                .append("totalCount", new Document("$sum", 1))
                .append("totalMinutes", new Document("$sum", "$minutes"))
                .append("totalSeconds", new Document("$sum", "$seconds"))
                .append("totalSeconds6", new Document("$sum", "$seconds6"));
        pipeline.add(new Document("$group", groupBy));

        MongoCollection<Document> collection = MongoUtil.INSTANCE.getCollection("bill_cdr_query_zj");
        return collection.aggregate(pipeline).allowDiskUse(true).iterator();
    }

    /**
     * 导出数据到excel
     *
     * @param filePath         excel文件路径
     * @param defaultSheetName 默认工作表名
     * @param iterator         数据
     */
    private void exportData(String filePath, String defaultSheetName, MongoCursor<Document> iterator) {
//        创建工作簿和表
        SXSSFWorkbook wb = ExcelUtil.INSTANCE.returnWorkBookGivenFileHandle(filePath, defaultSheetName);
        if (null == wb) {
            return;
        }

//        将列头添加至表
        SXSSFSheet sheet = ExcelUtil.INSTANCE.returnSheetFromWorkBook(wb);
        Map<Integer, String> headers = new HashMap<>();
        headers.put(0, "账户编号");
        headers.put(1, "400号码");
        headers.put(2, "条数");
        headers.put(3, "计费分钟");
        headers.put(4, "计费6秒数");
        headers.put(5, "计费秒数");
        headers.put(6, "计费费用（元）");
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
            rowCells.put(6, this.calculateTotalPrice(document));

            ExcelUtil.INSTANCE.insertRows(sheet, ExcelUtil.INSTANCE.getNextRowNum(), rowCells);
        }
        ExcelUtil.INSTANCE.saveExcelAndReset(wb, ExcelUtil.INSTANCE.getFilePath());
    }

    /**
     * 计算总价
     *
     * @param document 总计通话数据
     * @return 总价
     */
    private String calculateTotalPrice(Document document) {
        String accountId = document.get("_id", Document.class).getString("account");
        MongoCollection<Document> collection = MongoUtil.INSTANCE.getCollection("platform_account_product");
        Document data = collection.find(new Document("_id", accountId + "zj")).first();

        String type = document.getString("type");
        if (null != data) {
            String strategyType = "dialFeeStrategy";
            String feeType = "dialFee";
            if (type.equals("transfer")) {
                strategyType = "transferFeeStrategy";
                feeType = "transferFee";
                if (!data.containsKey("transferFeeStrategy")) {
                    strategyType = "dialFeeStrategy";
                    feeType = "dialFee";
                }
            }

            String strategy = data.getString(strategyType);
            Document dialFee = data.get(feeType, Document.class);
//            String strategy = document.getString("feeStrategy");
//            Document dialFee = data.get("dialFee", Document.class);
            String callerDistrictNo = data.getString("callerDistrictNo");
            String calleeDistrictNo = data.getString("calleeDistrictNo");
            boolean isLocal = false;
            if (callerDistrictNo.equals(calleeDistrictNo)) {
                isLocal = true;
            }
            Long local = dialFee.getLong("local");
            Long remote = dialFee.getLong("remote");
            Long prefixMin = dialFee.getLong("prefixMin");
            Long totalMinutes = document.getLong("totalMinutes");
            if ("minute".equals(strategy)) {
                if (isLocal) {
                    return local * totalMinutes / 10000.0 + "";
                } else {
                    return remote * totalMinutes / 10000.0 + "";
                }
            } else if ("minute3".equals(strategy)) {
                if (isLocal) {
                    long afterTotalMinutes = totalMinutes - 3 * document.getLong("totalCount");
                    return (prefixMin + afterTotalMinutes) / 10000.0 + "";
                } else {
                    return remote * totalMinutes / 10000.0 + "";
                }
            }
            return null;
        } else {
            return null;
        }
    }

    /**
     * 判断是否手机号
     *
     * @param number 待检验数据
     * @return true：是手机号，false：不是手机号
     */
    private boolean isMobileNo(String number) {
        if (number.matches("1[34578][0-9]{9}"))
            return true;
        return false;
    }

}
