package de.chojo.lolorito;

import de.chojo.lolorito.config.Configuration;
import de.chojo.lolorito.core.Data;
import de.chojo.lolorito.core.Threading;
import de.chojo.lolorito.core.Universalis;

import java.io.IOException;
import java.sql.SQLException;

public class Lolorito {
    private static Lolorito lolorito;

    public static void main(String[] args) throws SQLException, IOException, InterruptedException {
        lolorito = new Lolorito();
        lolorito.start();
    }

    private void start() throws SQLException, IOException, InterruptedException {
        Configuration configuration = Configuration.create();
        Threading threading = new Threading();

        Data data = Data.create(threading, configuration);

        Universalis.create(threading, data);
    }
}
