<template>
  <view class="page">
    <view v-if="messages.length === 0" class="card">
      <text class="muted">还没有聊天记录。在微信里复制对方消息并生成回复后，这里会显示历史。</text>
    </view>

    <view v-for="(item, index) in messages" :key="index" class="card">
      <view class="head">
        <text :class="item.sender === 'me' ? 'tag-me' : 'tag-other'">
          {{ item.sender === 'me' ? '我方回复' : '对方消息' }}
        </text>
        <text class="muted">{{ formatTime(item.timestamp) }}</text>
      </view>
      <text class="content">{{ item.content }}</text>
      <text v-if="item.emotion" class="muted">情绪：{{ item.emotion }} · 阶段 {{ item.stageId }} · {{ item.mode === 'LOCAL_MODEL' ? '本地模型' : '规则模式' }}</text>
    </view>
  </view>
</template>

<script>
import { getHistory } from '../../utils/bridge'

export default {
  data() {
    return { messages: [] }
  },
  onShow() {
    this.messages = getHistory() || []
  },
  methods: {
    formatTime(ts) {
      if (!ts) return ''
      const d = new Date(ts)
      const pad = (n) => (n < 10 ? '0' + n : '' + n)
      return `${d.getMonth() + 1}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
    }
  }
}
</script>

<style>
.head {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  margin-bottom: 6px;
}
.tag-me {
  color: #7c5cff;
  font-size: 12px;
}
.tag-other {
  color: #34c759;
  font-size: 12px;
}
.content {
  color: #f2f2f7;
  font-size: 14px;
  margin-bottom: 6px;
}
</style>
