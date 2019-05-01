import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.DeleteResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bson.Document;
import org.bson.types.ObjectId;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PostHandler implements HttpHandler {

    @Inject MongoClient mongoClient;
    MongoDatabase db;
    Document doc;
    MongoCollection<Document> collection;

    @Override
    public void handle(HttpExchange r) throws IOException {
        this.db = mongoClient.getDatabase("database");
        this.collection = db.getCollection("posts");
        try {
            if (r.getRequestMethod().equals("GET")) {
                handleGet(r);
            } else if (r.getRequestMethod().equals("PUT")) {
                handlePut(r);
            } else if (r.getRequestMethod().equals("DELETE")) {
                handleDelete(r);
            } else { //Request is something other than GET, PUT, DELETE
                r.sendResponseHeaders(405, -1);
            }
        } catch (IOException e) {
            r.sendResponseHeaders(500, -1);
        }
    }

    void handleGet(HttpExchange r) throws IOException {
        doc = httpToDoc(r);
        String response;
        String id = doc.getString("_id");
        String title = doc.getString("title");
        MongoCursor<Document> output;
        Document query = new Document();
        List<String> found = new ArrayList<>();
        //Create and execute our query
        if (id != null) { //id is available to use
            try {
                ObjectId oid = new ObjectId(id);
                query.append("_id", oid);
            } catch (IllegalArgumentException e) {
                r.sendResponseHeaders(404, -1);
                return;
            }
        } else if (title != null) { //id is not provided, title is available
            Document regQuery = new Document();
            regQuery.append("$regex", "\\b" + Pattern.quote(title) + "\\b");
            query.append("title", regQuery);
        } else { //no title or id is provided
            r.sendResponseHeaders(400, -1);
            return;
        }
        //If any documents match query
        if (collection.countDocuments(query) < 1) {
            r.sendResponseHeaders(404, -1);
            return;
        }
        output = collection.find(query).iterator();
        //Parse the output
        try {
            while (output.hasNext()) {
                found.add(output.next().toJson());
            }
        } finally {
            output.close();
        }
        //Send response
        response = found.toString();
        r.sendResponseHeaders(200, response.length());
        OutputStream os = r.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    void handlePut(HttpExchange r) throws IOException {
        doc = httpToDoc(r);
        String response;
        Document insert = new Document();
        Document output = new Document();
        String title = doc.getString("title");
        String author = doc.getString("author");
        String content = doc.getString("content");
        List<String> tags = doc.getList("tags", String.class);
        //Document must provide all the required attributes
        if (title == null || author == null || content == null || tags == null) {
            r.sendResponseHeaders(400, -1);
            return;
        }
        
        insert.append("title", title);
        insert.append("author", author);
        insert.append("content", content);
        insert.append("tags", tags);
        //Perform the query and validate its output
        collection.insertOne(insert);
        output.append("_id", insert.get("_id").toString()); //breaks with getString()
        response = output.toJson();
        //Send response
        r.sendResponseHeaders(200, response.length());
        OutputStream os = r.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    void handleDelete(HttpExchange r) throws IOException {
        doc = httpToDoc(r);
        //Document must provide the required attribute
        String id = doc.getString("_id");
        if (id == null) {
            r.sendResponseHeaders(400, -1);
            return;
        }
        //Ensure the id is valid
        Document query = new Document();
        try {
            ObjectId oid = new ObjectId(id);
            query.append("_id", oid);
        } catch (IllegalArgumentException e) {
            r.sendResponseHeaders(404, -1);
            return;
        }
        //Perform the query, validate its output and send response
        DeleteResult ack = collection.deleteOne(query);
        if (ack.getDeletedCount() == 1) {
            r.sendResponseHeaders(200, -1);
        } else {
            r.sendResponseHeaders(404, -1);
        }
    }

    public static Document httpToDoc(HttpExchange r) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(r.getRequestBody()))) {
            String body = br.lines().collect(Collectors.joining(System.lineSeparator()));
            return Document.parse(body);
        }
    }
}
