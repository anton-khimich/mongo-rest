import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.sun.net.httpserver.HttpServer;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;
import java.io.IOException;
import java.net.InetSocketAddress;

@Module (injects = {App.class}, library = true)
class DaggerModule {
    Config config;

    DaggerModule(Config cfg) {
        config = cfg;
    }

    @Provides
    @Singleton
    MongoClient provideMongoClient() {
        return MongoClients.create();
    }

    @Provides
    @Singleton
    HttpServer provideHttpServer() {
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(config.ip, config.port), 0);
        } catch (IOException e) {
            server = null;
        }
        return server;
    }
}
