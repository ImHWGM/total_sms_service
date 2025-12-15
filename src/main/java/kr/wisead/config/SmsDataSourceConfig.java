package kr.wisead.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * SMS DataSource Configuration (문자발송 모듈 DB)
 * - Database: MySQL (qvey)
 * - Mapper 패키지: kr.wisead.mapper.sms
 */
@Configuration
@MapperScan(
    basePackages = "kr.wisead.mapper.sms",
    sqlSessionFactoryRef = "smsSqlSessionFactory"
)
public class SmsDataSourceConfig {

    @Bean(name = "smsDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.sms")
    public DataSource smsDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "smsSqlSessionFactory")
    public SqlSessionFactory smsSqlSessionFactory(
            @Qualifier("smsDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(
            new PathMatchingResourcePatternResolver()
                .getResources("classpath:mapper/sms/**/*.xml")
        );

        // MyBatis 설정
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        factoryBean.setConfiguration(configuration);

        return factoryBean.getObject();
    }

    @Bean(name = "smsSqlSessionTemplate")
    public SqlSessionTemplate smsSqlSessionTemplate(
            @Qualifier("smsSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Bean(name = "smsTransactionManager")
    public PlatformTransactionManager smsTransactionManager(
            @Qualifier("smsDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
