import cc.CC_Billing;
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
        CC_Billing.INSTANCE.calculateCC("1464710400","1467302399","E:\\账户统计数据\\test111.xlsx","test");


    }

}
