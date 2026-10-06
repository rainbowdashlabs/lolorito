/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lolorito;

import com.google.inject.Guice;
import de.chojo.lolorito.config.Conf;
import de.chojo.lolorito.core.DatabaseBootstrap;
import de.chojo.lolorito.core.Threading;
import de.chojo.lolorito.web.Web;
import de.chojo.universalis.provider.items.Items;

import java.io.IOException;
import java.sql.SQLException;

public class Lolorito {

    static void main(String[] args) throws SQLException, IOException, InterruptedException {
        var conf = new Conf();
        var config = conf.main();
        conf.save();
        var threading = new Threading();

        var itemNameSupplier = Items.create();
        var dataSource = DatabaseBootstrap.bootstrap(threading, config);

        var injector = Guice.createInjector(new LoloritoModule(config, threading, dataSource, itemNameSupplier, conf));
        injector.getInstance(Web.class).start();
    }
}
