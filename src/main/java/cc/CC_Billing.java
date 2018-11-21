package cc;

import com.mongodb.BasicDBObject;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import mongodb.Mongodbjdbc;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.bson.Document;
import util.ExcelUtil;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum CC_Billing {
    INSTANCE;

    /**
     * 计算总计数据
     *
     * @param startTime        开始时间
     * @param endTime          结束时间
     * @param filePath         excel文件路径
     * @param defaultSheetName 默认工作表名
     */
    public void calculateCC(String startTime, String endTime, String filePath, String defaultSheetName) {
        MongoCursor<Document> iterator = null;
        try {
            iterator = this.queryData(startTime, endTime);
            this.exportData(filePath, defaultSheetName, iterator, startTime, endTime);
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
        Document project = new Document("account", 1)
                .append("feeStrategy", 1)
                .append("seconds", 1)
                .append("seconds6", 1)
                .append("minutes", 1);
        pipeline.add(new Document("$project", project));

//        分组条件
        Document groupBy = new Document("_id", new Document("account", "$account"))
                .append("totalCount", new Document("$sum", 1))
                .append("totalMinutes", new Document("$sum", "$minutes"))
                .append("totalSeconds", new Document("$sum", "$seconds"))
                .append("totalSeconds6", new Document("$sum", "$seconds6"));
        pipeline.add(new Document("$group", groupBy));

        MongoCollection<Document> collection = Mongodbjdbc.MongGetDom().getCollection("bill_cdr_query_zj");
        return collection.aggregate(pipeline).allowDiskUse(true).iterator();
    }

    /**
     * 导出数据到excel
     *
     * @param filePath         excel文件路径
     * @param defaultSheetName 默认工作表名
     * @param iterator         数据
     */
    private void exportData(String filePath, String defaultSheetName, MongoCursor<Document> iterator, String startTime, String endTime) {
//        创建工作簿和表
        SXSSFWorkbook wb = ExcelUtil.INSTANCE.returnWorkBookGivenFileHandle(filePath, defaultSheetName);
        if (null == wb) {
            return;
        }

//        将列头添加至表
        SXSSFSheet sheet = ExcelUtil.INSTANCE.returnSheetFromWorkBook(wb);
        Map<Integer, String> headers = new HashMap<>();
        headers.put(0, "账户编号");
        headers.put(1, "条数");
        headers.put(2, "计费分钟");
        headers.put(3, "计费6秒数");
        headers.put(4, "计费秒数");
        headers.put(5, "计费费用（元）");
        ExcelUtil.INSTANCE.insertRows(sheet, ExcelUtil.INSTANCE.getNextRowNum(), headers);

        while (iterator.hasNext()) {
            Document document = iterator.next();
            String account = document.get("_id", Document.class).getString("account");
            String totalCount = String.valueOf(document.getInteger("totalCount"));
            String totalMinutes = String.valueOf(document.getLong("totalMinutes"));
            String totalSeconds6 = String.valueOf(document.getLong("totalSeconds6"));
            String totalSeconds = String.valueOf(document.getLong("totalSeconds"));

            Map<Integer, String> rowCells = new HashMap<>();
            rowCells.put(0, account);
            rowCells.put(1, totalCount);
            rowCells.put(2, totalMinutes);
            rowCells.put(3, totalSeconds6);
            rowCells.put(4, totalSeconds);
            rowCells.put(5, this.calculateTotalPrice(account, startTime, endTime)+"");

            ExcelUtil.INSTANCE.insertRows(sheet, ExcelUtil.INSTANCE.getNextRowNum(), rowCells);
        }
        ExcelUtil.INSTANCE.saveExcelAndReset(wb, ExcelUtil.INSTANCE.getFilePath());
    }

    /**
     * 计算总价
     *
     * @param
     * @return 总价
     */
    private double calculateTotalPrice(String accountId, String startTime, String endTime) {
        //根据账户查询出电话号码
        MongoCollection<Document> collection_cc = Mongodbjdbc.MongGetDom().getCollection("bill_cdr_query_zj");
        MongoCursor<Document> iterator = getIterator(collection_cc, accountId,startTime, endTime );
        MongoCollection<Document> collection = Mongodbjdbc.MongGetDom().getCollection("platform_account_product");
        Document data = collection.find(new Document("_id", accountId + "_zj")).first();
        double total=0;
        while (iterator.hasNext()) {
            Document document = iterator.next();
            String type = document.getString("type");
            String did = document.getString("did");
            long minutes = document.getLong("minutes");
            long seconds = document.getLong("seconds");
            long seconds6 = document.getLong("seconds6");
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
                String callerDistrictNo = document.getString("callerDistrictNo");
                String calleeDistrictNo = document.getString("calleeDistrictNo");
                boolean isLocal = false;
                if (callerDistrictNo.equals(calleeDistrictNo)) {
                    isLocal = true;
                }
                if ("minute".equals(strategy)) {
                    Integer local = dialFee.getInteger("local");
                    Integer remote = dialFee.getInteger("remote");
                    Integer localTelPrice = dialFee.getInteger("localTel");
                    Integer remoteTelPrice = dialFee.getInteger("remoteTel");
                    if (isLocal) {
                        if (isMobileNo(did)) {
                            total=total+ local * minutes / 10000.0 ;
                        } else {
                            total=total+ localTelPrice * minutes / 10000.0 ;
                        }
                    } else {
                        if (isMobileNo(did)) {
                            total=total+ remote * minutes / 10000.0 ;
                        } else {
                            total=total+ remoteTelPrice * minutes / 10000.0 ;
                        }
                    }
                } else if ("minute3".equals(strategy)) {
                    Integer remotePrice = dialFee.getInteger("remote");
                    Integer localPrice = dialFee.getInteger("local");
                    Integer prefixMin = dialFee.getInteger("prefixMin");
                    if (isLocal) {
                        long afterTotalMinutes = minutes- 3;
                        if (afterTotalMinutes < 0) {
                            afterTotalMinutes = 0;
                        }
                        total=total+ (prefixMin + afterTotalMinutes) * localPrice / 10000.0 ;
                    } else {
                        total=total+ remotePrice * minutes / 10000.0 ;
                    }
                } else if ("second6".equals(strategy)) {
                    Integer remotePrice = dialFee.getInteger("remote");
                    Integer localPrice = dialFee.getInteger("local");
                    // 市话
                    if (isLocal) {
                        total=total+ localPrice * seconds6 / 10000.0 ;
                    }
                    // 长途
                    else {
                        total=total+ remotePrice *seconds6/ 10000.0 ;
                    }
                } else if ("minute3s6".equals(strategy)) {
                    Integer remotePrice = dialFee.getInteger("remote");
                    Integer localPrice = dialFee.getInteger("local");
                    Integer prefixMin = dialFee.getInteger("prefixMin");
                    // 市话
                    if (isLocal) {
                        long after3m = minutes - 3;
                        if (after3m < 0) {
                            after3m = 0;
                        }
                        total=total+ prefixMin + after3m * localPrice/10000.0;
                        }
                    // 长途
                    else {
                        total=total +remotePrice * seconds6/10000.0;

                    }
                }
            }
            }
            return total;
        }
        /**
         * 判断是否手机号
         *
         * @param number 待检验数据
         * @return true：是手机号，false：不是手机号
         */
        private boolean isMobileNo (String number){
            if (number.matches("1[34578][0-9]{9}"))
                return true;
            return false;
        }
        private MongoCursor<Document> getIterator (MongoCollection < Document > collection, String accountId, String
        startTime, String endTime){
            List<Document> pipeline = new ArrayList<>();

//        筛选条件
            Document match = new Document("beginTime", new Document("$gte", startTime))
                    .append("beginTime", new Document("$lte", endTime));
            pipeline.add(new Document("$match", match));
            //  返回字段
            Document project = new Document("account", 1)
                    .append("seconds", 1)
                    .append("seconds6", 1)
                    .append("minutes", 1)
                    .append("did", 1);
            pipeline.add(new Document("$project", project));
            FindIterable<Document> documents = collection.find(new Document().append("beginTime", new Document()
                    .append("$gte", startTime)
                    .append("$lte", endTime)).append("account",accountId)).noCursorTimeout(true);
            MongoCursor < Document > iterator = documents.iterator();
            return iterator;
        }
    }
