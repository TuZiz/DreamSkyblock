import com.google.gson.*;
import org.bukkit.configuration.file.YamlConfiguration;
import ym.dreamSkyblock.text.MessageService;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Uses the actual plugin renderer, including parsed dynamic tags and default italic=false. */
public final class DreamMenuTextProbe {
    public static void main(String[] args) throws Exception {
        var messages = new MessageService(new YamlConfiguration());
        var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        var output = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        String line;
        while ((line = input.readLine()) != null) {
            var payload = JsonParser.parseString(line).getAsJsonObject();
            Map<String,String> placeholders = new HashMap<>();
            payload.getAsJsonObject("placeholders").entrySet().forEach(e -> placeholders.put(e.getKey(), e.getValue().getAsString()));
            var component = messages.componentRaw(payload.get("text").getAsString(), placeholders);
            output.println(GsonComponentSerializer.gson().serialize(component));
        }
    }
}
