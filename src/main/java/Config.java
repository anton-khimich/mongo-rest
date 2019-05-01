import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class Config 
{
    @Inject Config() {}

    String ip = "0.0.0.0";
    int port = 8080;
}
