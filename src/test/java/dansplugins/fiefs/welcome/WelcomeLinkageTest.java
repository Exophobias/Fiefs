package dansplugins.fiefs.welcome;

import org.junit.jupiter.api.Test;
import java.net.URL;
import java.net.URLClassLoader;
import static org.junit.jupiter.api.Assertions.*;

class WelcomeLinkageTest {
    @Test void ownerAndLifecycleGuardResolveWithTheOptionalApiAbsent() throws Exception {
        URL classes = dansplugins.fiefs.Fiefs.class.getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{classes}, getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("com.github.exophobias.patriamwelcome.")) throw new ClassNotFoundException(name);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> type = findLoadedClass(name);
                    if (type == null && name.startsWith("dansplugins.fiefs.")) {
                        try { type = findClass(name); } catch (ClassNotFoundException ignored) { }
                    }
                    if (type == null) type = super.loadClass(name, false);
                    if (resolve) resolveClass(type);
                    return type;
                }
            }
        }) {
            for (String name : new String[]{"dansplugins.fiefs.Fiefs", "dansplugins.fiefs.welcome.WelcomeFeatureIntegration"}) {
                Class<?> type = Class.forName(name, false, loader);
                assertNotNull(type.getDeclaredConstructors());
                assertNotNull(type.getDeclaredFields());
                assertNotNull(type.getDeclaredMethods());
            }
            assertThrows(LinkageError.class, () -> Class.forName("dansplugins.fiefs.welcome.WelcomeFeatureProvider", false, loader));
        }
    }
}
