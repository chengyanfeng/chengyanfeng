import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import mongodb.Mongodbjdbc;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Main {

    private static final Logger logger = LoggerFactory
            .getLogger(Mongodbjdbc.class);
    public static void main(String[] args){
        //获取数据库链接
        MongoDatabase mongoconnect= Mongodbjdbc.MongoConnet();
        MongoCollection<Document> cdr_query_cc=null;
        if (mongoconnect!=null){
            //获取文档集合
             cdr_query_cc = mongoconnect.getCollection("bill_cdr_query_cc");
        }else {
            logger.error("数据库链接失败");
        }

        FindIterable<Document> findIterable = cdr_query_cc.find();
        MongoCursor<Document> mongoCursor = findIterable.iterator();
        while(mongoCursor.hasNext()){
            System.out.println(mongoCursor.next());
        }

        Bson filter = Filters.eq("count", 0);


    }

}
