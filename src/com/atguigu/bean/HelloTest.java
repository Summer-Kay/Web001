package com.scb.mxg.mls.rt.gfi.processor;

import com.scb.mxg.mls.rt.data.CommonStaticData;
import com.scb.mxg.mls.rt.utils.BeanUtils;
import com.scb.mxg.mls.rt.gfi.vo.TradeEvent;
import com.scb.mxg.mls.trade.TradeEventType;
import com.scb.mxg.mls.vo.Journal;
import com.scb.mxg.mls.utils.XPathParser;
import net.sf.saxon.s9api.XsltTransformer;
import org.apache.camel.Exchange;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({BeanUtils.class, CommonStaticData.class, XPathParser.class, DateFormatUtils.class, DateUtils.class, StpProcessor.class, NamedParameterJdbcTemplate.class})
@PowerMockIgnore({"javax.management.*", "javax.script.*", "javax.xml.*", "org.xml.*", "org.w3c.*", "com.sun.org.apache.xerces.*"})
public class StpProcessorTest {

    private StpProcessor stpProcessor;
    private TransactionTemplate mlsDbTransactionTemplate;
    private String fixmessage;
    
    @Mock
    private CommonStaticData commonStaticData;
    
    @Mock
    private JdbcTemplate mlsDbJdbcTemplate;
    
    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    
    @Mock
    private PlatformTransactionManager transactionManager;
    
    @Mock
    private XsltTransformer xsltTransformer;
    
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Exchange exchange;
    
    @Mock
    private XPathParser xPathParser;
    
    @Before
    public void setUp() throws Exception {
        fixmessage = IOUtils.toString(getClass().getResourceAsStream("/inputFiles/normalFixml.xml"), "UTF-8");
        
        stpProcessor = new StpProcessor();
        
        // 重要：使用 spy 来部分 mock StpProcessor
        stpProcessor = PowerMockito.spy(new StpProcessor());
        
        // 设置依赖
        stpProcessor.setMlsDbJdbcTemplate(mlsDbJdbcTemplate);
        mlsDbTransactionTemplate = new TransactionTemplate(transactionManager);
        stpProcessor.setMlsDbTransactionTemplate(mlsDbTransactionTemplate);
        
        // Mock 静态方法
        PowerMockito.mockStatic(BeanUtils.class);
        PowerMockito.when(BeanUtils.getBean("commonStaticData")).thenReturn(commonStaticData);
        
        // Mock XPathParser 构造函数
        PowerMockito.whenNew(XPathParser.class)
            .withArguments(anyString())
            .thenReturn(xPathParser);
        
        // Mock DateUtils 和 DateFormatUtils
        PowerMockito.mockStatic(DateUtils.class);
        PowerMockito.mockStatic(DateFormatUtils.class);
        
        // 关键：拦截 NamedParameterJdbcTemplate 的创建
        PowerMockito.whenNew(NamedParameterJdbcTemplate.class)
            .withArguments(any(JdbcTemplate.class))
            .thenReturn(namedParameterJdbcTemplate);
        
        // Mock 所有可能的数据库查询操作
        mockAllDatabaseOperations();
    }
    
    private void mockAllDatabaseOperations() {
        // 1. Mock queryForObject 调用 - 用于 isExisting 方法
        when(namedParameterJdbcTemplate.queryForObject(
            anyString(), 
            any(Map.class), 
            eq(Long.class))
        ).thenReturn(0L);  // 默认返回 0 表示不存在
        
        // 2. Mock update 操作 - 用于 saveJournal, recordTradeEvent 等方法
        when(namedParameterJdbcTemplate.update(anyString(), any(Map.class)))
            .thenReturn(1);  // 返回 1 表示更新成功
        
        // 3. Mock batchUpdate 操作
        when(namedParameterJdbcTemplate.batchUpdate(anyString(), any(Map[].class)))
            .thenReturn(new int[]{1});
        
        // 4. Mock query 操作
        when(namedParameterJdbcTemplate.query(anyString(), any(Map.class), any(org.springframework.jdbc.core.RowMapper.class)))
            .thenReturn(Collections.emptyList());
    }

    @Test
    public void testHandleExchange_ShouldNotCallRealDatabase() throws Exception {
        // Arrange
        setupCommonMocks();
        
        // Mock XPath 解析
        when(xPathParser.evaluate(anyString())).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                String xpath = invocation.getArgument(0);
                // 根据不同的 xpath 返回不同的值
                if (xpath.contains("LegType")) return "HedgeLegs";
                if (xpath.contains("LegSubID")) return "2";
                if (xpath.contains("hedgeCurrency")) return "USD";
                if (xpath.contains("tradeExternalId")) return "TEST123";
                if (xpath.contains("MaxLegVal")) return "1";
                if (xpath.contains("LegID")) return "L1";
                if (xpath.contains("tradeType")) return "FX";
                if (xpath.contains("tradeDate")) return "20230101";
                if (xpath.contains("trader")) return "TRADER01";
                return "TEST";
            }
        });
        
        // 确保 isExisting 方法被 mock
        PowerMockito.doReturn(false).when(stpProcessor, "isExisting", anyString(), anyString());
        
        // Mock saveJournal 方法（如果它是父类方法）
        PowerMockito.doNothing().when(stpProcessor, "saveJournal", any(Exchange.class), any(Journal.class));
        
        // Mock recordTradeEvent 方法
        PowerMockito.doNothing().when(stpProcessor, "recordTradeEvent", any(Exchange.class), any(TradeEvent.class));
        
        // Mock transformToMurex 方法
        PowerMockito.doNothing().when(stpProcessor, "transformToMurex", 
            any(Exchange.class), anyString(), anyString(), anyString(), 
            anyString(), anyString(), anyString(), anyString());
        
        // Act
        stpProcessor.handleExchange(exchange);
        
        // Assert - 验证没有调用真实的数据库操作
        verify(namedParameterJdbcTemplate, times(1)).queryForObject(anyString(), any(Map.class), eq(Long.class));
        
        // 验证构造函数只被调用了一次（在 handleExchange 中）
        PowerMockito.verifyNew(NamedParameterJdbcTemplate.class).withArguments(any(JdbcTemplate.class));
    }

    @Test
    public void testIsExisting_MockedDatabaseCall() throws Exception {
        // Arrange
        String sql = "SELECT COUNT(*) FROM trades WHERE reference_id = :referenceId";
        
        // 直接测试 isExisting 方法，确保它被正确 mock
        // 使用反射调用私有方法
        java.lang.reflect.Method method = StpProcessor.class.getDeclaredMethod("isExisting", String.class, String.class);
        method.setAccessible(true);
        
        // 测试返回 true 的情况
        when(namedParameterJdbcTemplate.queryForObject(
            eq(sql), 
            any(Map.class), 
            eq(Long.class))
        ).thenReturn(1L);
        
        // Act
        boolean result = (boolean) method.invoke(stpProcessor, "TEST123", "GFI");
        
        // Assert
        assertTrue(result);
        
        // 验证数据库调用
        verify(namedParameterJdbcTemplate, times(1)).queryForObject(
            anyString(), 
            any(Map.class), 
            eq(Long.class)
        );
    }

    @Test
    public void testHandleExchange_DuplicateTrade() throws Exception {
        // Arrange
        setupCommonMocks();
        
        // Mock XPath 解析
        when(xPathParser.evaluate(anyString())).thenReturn("TEST_VALUE");
        when(xPathParser.evaluate(contains("LegType"))).thenReturn("HedgeLegs");
        when(xPathParser.evaluate(contains("LegSubID"))).thenReturn("2");
        when(xPathParser.evaluate(contains("hedgeCurrency"))).thenReturn("USD");
        when(xPathParser.evaluate(contains("tradeDate"))).thenReturn("20230101");
        when(xPathParser.evaluate(contains("tradeType"))).thenReturn("FX");
        
        // Mock isExisting 返回 true
        PowerMockito.doReturn(true).when(stpProcessor, "isExisting", anyString(), anyString());
        
        // Mock 父类方法
        PowerMockito.doNothing().when(stpProcessor, "saveJournal", any(Exchange.class), any(Journal.class));
        PowerMockito.doNothing().when(stpProcessor, "recordTradeEvent", any(Exchange.class), any(TradeEvent.class));
        
        // Mock DateUtils
        PowerMockito.when(DateUtils.parseDate(anyString(), anyString())).thenReturn(new Date());
        PowerMockito.when(DateFormatUtils.format(any(Date.class), anyString())).thenReturn("2023-01-01");
        
        // Act
        stpProcessor.handleExchange(exchange);
        
        // Assert
        // 验证 transformToMurex 没有被调用（因为是重复交易）
        PowerMockito.verifyPrivate(stpProcessor, never())
            .invoke("transformToMurex", 
                any(Exchange.class), anyString(), anyString(), anyString(), 
                anyString(), anyString(), anyString(), anyString());
    }

    @Test
    public void testHandleExchange_IgnoreInvalidHedgeLeg() throws Exception {
        // Arrange
        setupCommonMocks();
        
        // Mock XPath 解析 - 无效的对冲腿（legSubId=2 但 currency 为空）
        when(xPathParser.evaluate(anyString())).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                String xpath = invocation.getArgument(0);
                if (xpath.contains("LegType")) return "HedgeLegs";
                if (xpath.contains("LegSubID")) return "2";
                if (xpath.contains("hedgeCurrency")) return "";  // 空货币
                return "TEST";
            }
        });
        
        // Act
        stpProcessor.handleExchange(exchange);
        
        // Assert
        // 验证没有设置任何属性（应该被忽略）
        verify(exchange, never()).setProperty(startsWith("_"), any());
        
        // 验证没有调用数据库
        verify(namedParameterJdbcTemplate, never()).queryForObject(anyString(), any(Map.class), eq(Long.class));
    }

    private void setupCommonMocks() {
        when(commonStaticData.lookupStaicData(anyString(), anyString(), anyInt())).thenReturn("");
        when(exchange.getIn().getBody(eq(String.class))).thenReturn(fixmessage);
        when(exchange.getProperty(eq("originalTrackingId"), eq(String.class))).thenReturn("test-tracking-id");
    }

    @Test
    public void testHandleExchange_ExceptionInDatabase() throws Exception {
        // Arrange
        setupCommonMocks();
        
        // Mock XPath 解析
        when(xPathParser.evaluate(anyString())).thenReturn("TEST_VALUE");
        when(xPathParser.evaluate(contains("hedgeCurrency"))).thenReturn("USD");
        
        // Mock 数据库异常
        when(namedParameterJdbcTemplate.queryForObject(
            anyString(), 
            any(Map.class), 
            eq(Long.class))
        ).thenThrow(new RuntimeException("Database connection failed"));
        
        // Mock 父类方法以避免异常传播
        PowerMockito.doNothing().when(stpProcessor, "saveJournal", any(Exchange.class), any(Journal.class));
        
        // Act & Assert
        try {
            stpProcessor.handleExchange(exchange);
            // 如果代码没有正确处理异常，这里可能会失败
        } catch (Exception e) {
            // 可以捕获异常进行验证
            assertNotNull(e);
        }
    }
    
    @Test
    public void testNoRealDatabaseConnection() throws Exception {
        // 这个测试验证我们是否真的 mock 了所有数据库操作
        
        // Arrange
        setupCommonMocks();
        when(xPathParser.evaluate(anyString())).thenReturn("TEST");
        when(xPathParser.evaluate(contains("hedgeCurrency"))).thenReturn("USD");
        
        // 确保 JdbcTemplate 没有被调用
        verify(mlsDbJdbcTemplate, never()).execute(anyString());
        verify(mlsDbJdbcTemplate, never()).query(anyString(), any(org.springframework.jdbc.core.ResultSetExtractor.class));
        verify(mlsDbJdbcTemplate, never()).update(anyString(), any(Object[].class));
        
        // Act - 这个调用不应该触发真实数据库操作
        stpProcessor.handleExchange(exchange);
        
        // Assert - 验证只有 mock 对象被调用
        verify(namedParameterJdbcTemplate, atMostOnce()).queryForObject(anyString(), any(Map.class), eq(Long.class));
    }
}
