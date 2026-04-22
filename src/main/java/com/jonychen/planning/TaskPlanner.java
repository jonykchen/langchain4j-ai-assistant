package com.jonychen.planning;

import java.util.List;
import java.util.Map;

/**
 * 任务规划器接口
 *
 * @author jonychen
 */
public interface TaskPlanner {

    /**
     * 根据目标生成执行计划
     *
     * @param goal 任务目标
     * @param context 任务上下文
     * @return 执行步骤列表
     */
    List<Step> plan(String goal, Map<String, Object> context);

    /**
     * 根据执行情况重新规划
     *
     * @param task 当前任务
     * @param failedStep 失败的步骤
     * @return 新的执行步骤列表
     */
    List<Step> replan(Task task, Step failedStep);

    /**
     * 判断是否需要规划
     *
     * @param goal 任务目标
     * @return 是否需要规划
     */
    boolean needsPlanning(String goal);

    /** 获取规划器名称 */
    String getName();
}
