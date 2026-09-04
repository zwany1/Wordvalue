<template>
  <view class="page">
    <view class="card">
      <text class="card-title">策略引擎</text>
      <text class="muted">输入法内置七阶段关系推进模型、情绪识别、对话策略与安全边界规则，全部离线运行。此页展示当前启用的能力。</text>
    </view>

    <view v-for="item in capabilities" :key="item.name" class="card">
      <text class="card-title">{{ item.name }}</text>
      <text class="muted">{{ item.desc }}</text>
    </view>
  </view>
</template>

<script>
export default {
  data() {
    return {
      capabilities: [
        { name: '① Relationship Stage', desc: '关系阶段判断，默认 Stage 2（建立好感）；检测到亲昵称呼、约定见面等强信号时自动提升阶段' },
        { name: '② 情绪识别', desc: '开心 / 难过 / 疲惫 / 生气 / 抱怨 / 冷淡 / 暧昧 / 拒绝 / 普通 九类情绪分类' },
        { name: '③ 对话策略', desc: '情绪→策略链映射：先回应情绪再轻度延伸；拒绝信号→尊重并体面收尾' },
        { name: '④ 回复风格', desc: '自然 / 温柔 / 幽默三种风格各生成一条，点击即填入微信输入框' },
        { name: '安全边界', desc: '检测到明确的拒绝与不适信号时，只给出体面收尾，停止一切推进' }
      ]
    }
  }
}
</script>
