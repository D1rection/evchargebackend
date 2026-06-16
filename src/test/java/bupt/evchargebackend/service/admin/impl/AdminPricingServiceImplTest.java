package bupt.evchargebackend.service.admin.impl;

import bupt.evchargebackend.common.exception.BusinessException;
import bupt.evchargebackend.dto.PricingRuleRequest.PeriodConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 时段重叠检测算法测试。
 */
class AdminPricingServiceImplTest {

    private static PeriodConfig pc(String name, String start, String end) {
        PeriodConfig c = new PeriodConfig();
        c.setPeriodName(name);
        c.setStartTime(start);
        c.setEndTime(end);
        c.setElectricityPrice(1.0);
        return c;
    }

    // ── 不重叠场景 ──

    @Test
    void shouldPassWhenNoOverlap() {
        List<PeriodConfig> configs = List.of(
                pc("PEAK", "08:00", "10:00"),
                pc("NORMAL", "10:00", "19:00"),
                pc("PEAK", "19:00", "21:00"),
                pc("NORMAL", "21:00", "00:00"),
                pc("VALLEY", "00:00", "08:00")
        );
        assertDoesNotThrow(() -> AdminPricingServiceImpl.validateNoOverlap(configs));
    }

    @Test
    void shouldPassWhenAdjacent() {
        // 相邻边界（半开区间，10:00结束 与 10:00开始 不重叠）
        List<PeriodConfig> configs = List.of(
                pc("PEAK", "08:00", "10:00"),
                pc("NORMAL", "10:00", "12:00")
        );
        assertDoesNotThrow(() -> AdminPricingServiceImpl.validateNoOverlap(configs));
    }

    @Test
    void shouldPassWhenSinglePeriod() {
        List<PeriodConfig> configs = List.of(
                pc("NORMAL", "00:00", "24:00")
        );
        assertDoesNotThrow(() -> AdminPricingServiceImpl.validateNoOverlap(configs));
    }

    // ── 重叠场景 ──

    @Test
    void shouldDetectSimpleOverlap() {
        List<PeriodConfig> configs = List.of(
                pc("PEAK", "08:00", "10:00"),
                pc("NORMAL", "09:00", "12:00")  // 09:00 < 10:00，重叠
        );
        BusinessException ex = assertThrows(BusinessException.class,
                () -> AdminPricingServiceImpl.validateNoOverlap(configs));
        assertTrue(ex.getMessage().contains("重叠"));
    }

    @Test
    void shouldDetectExactDuplicate() {
        List<PeriodConfig> configs = List.of(
                pc("PEAK", "08:00", "10:00"),
                pc("PEAK", "08:00", "10:00")
        );
        assertThrows(BusinessException.class,
                () -> AdminPricingServiceImpl.validateNoOverlap(configs));
    }

    @Test
    void shouldDetectContainedPeriod() {
        // 一个完全包含另一个
        List<PeriodConfig> configs = List.of(
                pc("NORMAL", "08:00", "20:00"),
                pc("PEAK", "10:00", "12:00")
        );
        assertThrows(BusinessException.class,
                () -> AdminPricingServiceImpl.validateNoOverlap(configs));
    }

    // ── 跨午夜场景 ──

    @Test
    void shouldPassWhenValleyCrossesMidnight() {
        List<PeriodConfig> configs = List.of(
                pc("PEAK", "08:00", "10:00"),
                pc("NORMAL", "10:00", "21:00"),
                pc("VALLEY", "21:00", "08:00")  // 跨午夜，与前后都不重叠
        );
        assertDoesNotThrow(() -> AdminPricingServiceImpl.validateNoOverlap(configs));
    }

    @Test
    void shouldDetectOverlapWithMidnightCrosser() {
        // NORMAL 07:00-12:00 与 VALLEY 21:00-08:00 在 07:00-08:00 重叠
        List<PeriodConfig> configs = List.of(
                pc("VALLEY", "21:00", "08:00"),
                pc("NORMAL", "07:00", "12:00")
        );
        assertThrows(BusinessException.class,
                () -> AdminPricingServiceImpl.validateNoOverlap(configs));
    }

    @Test
    void shouldDetectBothCrossMidnight() {
        // 两个都跨午夜，必然重叠
        List<PeriodConfig> configs = List.of(
                pc("VALLEY", "22:00", "06:00"),
                pc("PEAK", "23:00", "05:00")
        );
        assertThrows(BusinessException.class,
                () -> AdminPricingServiceImpl.validateNoOverlap(configs));
    }

    @Test
    void shouldPassWhenCrossMidnightTouchesBoundary() {
        // 跨午夜时段与不跨午夜时段在边界相邻
        List<PeriodConfig> configs = List.of(
                pc("VALLEY", "22:00", "08:00"),
                pc("PEAK", "08:00", "10:00")  // 08:00 正好是 VALLEY 结束
        );
        assertDoesNotThrow(() -> AdminPricingServiceImpl.validateNoOverlap(configs));
    }

    // ── overlap 方法直接测试 ──

    @Test
    void overlaps_NeitherCrosses_ShouldBeTrue() {
        assertTrue(AdminPricingServiceImpl.overlaps(
                pc("A", "08:00", "12:00"), pc("B", "10:00", "14:00")));
    }

    @Test
    void overlaps_NeitherCrosses_ShouldBeFalse() {
        assertFalse(AdminPricingServiceImpl.overlaps(
                pc("A", "08:00", "10:00"), pc("B", "10:00", "12:00")));
    }

    @Test
    void overlaps_OneCrosses_ShouldBeTrue() {
        // VALLEY 21:00-08:00 与 NORMAL 07:30-12:00 重叠 (07:30-08:00)
        assertTrue(AdminPricingServiceImpl.overlaps(
                pc("VALLEY", "21:00", "08:00"), pc("NORMAL", "07:30", "12:00")));
    }

    @Test
    void overlaps_OneCrosses_ShouldBeFalse() {
        // VALLEY 21:00-08:00 与 PEAK 08:00-10:00 不重叠 (刚好相邻)
        assertFalse(AdminPricingServiceImpl.overlaps(
                pc("VALLEY", "21:00", "08:00"), pc("PEAK", "08:00", "10:00")));
    }

    @Test
    void overlaps_BothCross_ShouldBeTrue() {
        assertTrue(AdminPricingServiceImpl.overlaps(
                pc("A", "22:00", "06:00"), pc("B", "23:00", "05:00")));
    }
}
