package com.example.approval.config;

import com.example.approval.mapper.CommonMapper;
import com.example.approval.mapper.FlowableIdentityMapper;
import com.example.approval.mapper.UserMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.boot.autoconfigure.MybatisProperties;
import org.mybatis.spring.boot.autoconfigure.SpringBootVFS;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * Splits MyBatis across the two DataSources defined in {@link DataSourceConfig}.
 *
 * <p>Spring Boot's {@code mybatis-spring-boot-starter} auto-configuration
 * only ever builds ONE {@link SqlSessionFactory}, bound to whichever
 * {@code DataSource} is {@code @Primary} - here, {@code primaryDataSource}
 * (MySQL, alongside Flowable's own tables). {@code CommonMapper.xml} is
 * Oracle SQL and has to run against {@code externalDataSource} instead, so
 * as soon as we declare our own {@code SqlSessionFactory} bean(s), Spring
 * Boot's single-factory auto-configuration backs off entirely
 * ({@code @ConditionalOnMissingBean}) and this class owns both from here on.
 * {@code mybatis.mapper-locations} in application.yml is no longer consumed
 * once this class exists - each factory below points at its own XML file
 * explicitly instead.</p>
 *
 * <p>{@code UserMapper.xml} -&gt; primaryDataSource (MySQL)<br>
 * {@code CommonMapper.xml} -&gt; externalDataSource (Oracle SIS/HRS schema)</p>
 *
 * <p><b>Not yet handled:</b> Spring's default transaction manager binds to
 * the {@code @Primary} DataSource, so {@code @Transactional} on
 * {@code CommonService}/{@code SISOC} does not actually wrap the Oracle
 * calls in a managed transaction - each MyBatis statement against
 * {@code externalDataSource} just autocommits on its own connection. Harmless
 * for CommonMapper's read-heavy queries and the single-row
 * {@code processSynchUser} insert, but worth knowing if you ever need
 * multi-statement atomicity against Oracle - you'd need a second
 * {@code DataSourceTransactionManager} bound to {@code externalDataSource}
 * and an explicit {@code @Transactional("externalTransactionManager")}.</p>
 */
@Configuration
public class MyBatisConfig {

    // ------------------------------------------------------------------
    // Primary (MySQL) - UserMapper
    // ------------------------------------------------------------------

    @Bean(name = "primarySqlSessionFactory")
    @Primary
    public SqlSessionFactory primarySqlSessionFactory(
            @Qualifier("primaryDataSource") DataSource primaryDataSource,
            MybatisProperties mybatisProperties) throws Exception {
        return buildSqlSessionFactory(primaryDataSource, mybatisProperties, "classpath:mapper/UserMapper.xml");
    }

    @Bean(name = "primarySqlSessionTemplate")
    @Primary
    public SqlSessionTemplate primarySqlSessionTemplate(
            @Qualifier("primarySqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Bean
    public MapperFactoryBean<UserMapper> userMapper(
            @Qualifier("primarySqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        MapperFactoryBean<UserMapper> mapperFactoryBean = new MapperFactoryBean<>(UserMapper.class);
        mapperFactoryBean.setSqlSessionFactory(sqlSessionFactory);
        return mapperFactoryBean;
    }

    // ------------------------------------------------------------------
    // External (Oracle) - CommonMapper
    // ------------------------------------------------------------------

    @Bean(name = "externalSqlSessionFactory")
    public SqlSessionFactory externalSqlSessionFactory(
            @Qualifier("externalDataSource") DataSource externalDataSource,
            MybatisProperties mybatisProperties) throws Exception {
        // Both CommonMapper.xml and FlowableIdentityMapper.xml are Oracle SQL
        // (NVL, dic_pkg.decrypt_data, FLOWABLE_USERS_VW) -> externalDataSource.
        return buildSqlSessionFactory(externalDataSource, mybatisProperties,
                "classpath:mapper/CommonMapper.xml",
                "classpath:mapper/FlowableIdentityMapper.xml");
    }

    @Bean(name = "externalSqlSessionTemplate")
    public SqlSessionTemplate externalSqlSessionTemplate(
            @Qualifier("externalSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Bean
    public MapperFactoryBean<CommonMapper> commonMapper(
            @Qualifier("externalSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        MapperFactoryBean<CommonMapper> mapperFactoryBean = new MapperFactoryBean<>(CommonMapper.class);
        mapperFactoryBean.setSqlSessionFactory(sqlSessionFactory);
        return mapperFactoryBean;
    }

    @Bean
    public MapperFactoryBean<FlowableIdentityMapper> flowableIdentityMapper(
            @Qualifier("externalSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        MapperFactoryBean<FlowableIdentityMapper> mapperFactoryBean = new MapperFactoryBean<>(FlowableIdentityMapper.class);
        mapperFactoryBean.setSqlSessionFactory(sqlSessionFactory);
        return mapperFactoryBean;
    }

    // ------------------------------------------------------------------
    // Shared factory-building logic
    // ------------------------------------------------------------------

    /**
     * Reuses whatever {@code mybatis.type-aliases-package} / {@code mybatis.configuration.*}
     * you already have in application.yml (via the auto-registered
     * {@link MybatisProperties} bean), but points {@code mapperLocations} at
     * just the one XML file this factory owns instead of the shared
     * {@code classpath:mapper/*.xml} glob.
     */
    private SqlSessionFactory buildSqlSessionFactory(DataSource dataSource,
                                                       MybatisProperties mybatisProperties,
                                                       String... mapperLocationPatterns) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setVfs(SpringBootVFS.class);

        if (mybatisProperties.getTypeAliasesPackage() != null) {
            factoryBean.setTypeAliasesPackage(mybatisProperties.getTypeAliasesPackage());
        }
        if (mybatisProperties.getConfiguration() != null) {
            //factoryBean.setConfiguration(mybatisProperties.getConfiguration());
        }
        factoryBean.setMapperLocations(resolveMapperLocations(mapperLocationPatterns));

        return factoryBean.getObject();
    }

    private Resource[] resolveMapperLocations(String... patterns) throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        java.util.List<Resource> resources = new java.util.ArrayList<>();
        for (String pattern : patterns) {
            for (Resource resource : resolver.getResources(pattern)) {
                resources.add(resource);
            }
        }
        return resources.toArray(new Resource[0]);
    }
}
