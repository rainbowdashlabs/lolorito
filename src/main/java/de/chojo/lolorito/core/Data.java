package de.chojo.lolorito.core;

import com.zaxxer.hikari.HikariDataSource;
import de.chojo.logutil.marker.LogNotify;
import de.chojo.lolorito.config.Configuration;
import de.chojo.lolorito.dao.Items;
import de.chojo.lolorito.dao.Listings;
import de.chojo.lolorito.dao.Sales;
import de.chojo.lolorito.dao.Users;
import de.chojo.sadu.base.QueryFactory;
import de.chojo.sadu.databases.PostgreSql;
import de.chojo.sadu.datasource.DataSourceCreator;
import de.chojo.sadu.mapper.RowMapperRegistry;
import de.chojo.sadu.updater.QueryReplacement;
import de.chojo.sadu.updater.SqlUpdater;
import de.chojo.sadu.wrapper.QueryBuilderConfig;
import de.chojo.universalis.provider.NameSupplier;
import de.chojo.universalis.worlds.DataCenter;
import de.chojo.universalis.worlds.Region;
import de.chojo.universalis.worlds.World;
import de.chojo.universalis.worlds.Worlds;
import org.slf4j.Logger;

import java.io.IOException;
import java.sql.SQLException;

import static org.slf4j.LoggerFactory.getLogger;

public class Data {
    private static final Logger log = getLogger(Data.class);
    private final Threading threading;
    private final Configuration configuration;
    private HikariDataSource dataSource;
    private Listings listings;
    private Sales sales;
    private Items items;
    private NameSupplier itemNameSupplier;
    private Users users;

    private Data(Threading threading, Configuration configuration) {
        this.threading = threading;
        this.configuration = configuration;
    }

    public static Data create(Threading threading, Configuration configuration) throws SQLException, IOException, InterruptedException {
        var data = new Data(threading, configuration);
        data.init();
        return data;
    }

    public void init() throws SQLException, IOException, InterruptedException {
        initItems();
        configure();
        initConnection();
        updateDatabase();
        initDao();
    }

    private void initItems() throws IOException, InterruptedException {
        itemNameSupplier = de.chojo.universalis.provider.items.Items.create();
    }

    public void initConnection() {
        dataSource = getConnectionPool();
    }

    private void updateDatabase() throws IOException, SQLException {
        var schema = configuration.database().schema();
        SqlUpdater.builder(dataSource, PostgreSql.get())
                .setReplacements(new QueryReplacement("lolorito", schema))
                .setVersionTable(schema + ".lolorito_version")
                .setSchemas(schema)
                .execute();
        log.info("Updating worlds");

        QueryFactory factory = new QueryFactory(dataSource);
        for (Region region : Worlds.regions()) {
            for (World world : region.worlds()) {
                DataCenter dataCenter = world.dataCenter();
                factory.builder()
                        .query("""
                               INSERT INTO worlds(region_name, data_center, data_center_name, world, world_name)
                               VALUES(?,?,?,?,?)
                               ON CONFLICT (data_center, world)
                                    DO UPDATE SET region_name = excluded.region_name,
                                                  data_center_name = excluded.data_center_name,
                                                  world_name = excluded.world_name
                               """)
                        .parameter(stmt -> stmt.setString(region.name())
                                               .setInt(dataCenter.id())
                                               .setString(dataCenter.name())
                                               .setInt(world.id())
                                               .setString(world.name())
                        ).insert()
                        .send();
            }
        }
    }

    private void configure() {
        RowMapperRegistry registry = new RowMapperRegistry();
        log.info("Configuring QueryBuilder");
        var logger = getLogger("DbLogger");
        QueryBuilderConfig.setDefault(QueryBuilderConfig.builder()
                .withExceptionHandler(err -> logger.error(LogNotify.NOTIFY_ADMIN, "An error occured during a database request", err))
                .withExecutor(threading.botWorker())
                .build());
    }

    private void initDao() {
        log.info("Creating DAOs");
        listings = new Listings(dataSource);
        sales = new Sales(dataSource);
        items = new Items(dataSource, itemNameSupplier);
        users = new Users(dataSource);
    }

    private HikariDataSource getConnectionPool() {
        log.info("Creating connection pool.");
        var data = configuration.database();
        return DataSourceCreator.create(PostgreSql.get())
                .configure(config -> config
                        .host(data.host())
                        .port(data.port())
                        .user(data.user())
                        .password(data.password())
                        .database(data.database()))
                .create()
                .withMaximumPoolSize(data.poolSize())
                .withThreadFactory(Threading.createThreadFactory(threading.hikariGroup()))
                .forSchema(data.schema())
                .build();
    }

    public void shutDown() {
        dataSource.close();
    }

    public Listings listings() {
        return listings;
    }

    public Sales sales() {
        return sales;
    }

    public Items items() {
        return items;
    }

    public Users users() {
        return users;
    }
}
