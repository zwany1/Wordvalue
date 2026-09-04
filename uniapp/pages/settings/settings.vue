<template>
  <view class="page">
    <view class="card">
      <text class="card-title">默认风格</text>
      <view class="segment">
        <text
          v-for="s in styles"
          :key="s.value"
          :class="['seg-item', settings.defaultStyle === s.value ? 'seg-active' : '']"
          @tap="pickStyle(s.value)"
        >{{ s.label }}</text>
      </view>
    </view>

    <view class="card">
      <text class="card-title">最大回复长度</text>
      <view class="segment">
        <text
          v-for="n in lengths"
          :key="n"
          :class="['seg-item', settings.maxReplyLength === n ? 'seg-active' : '']"
          @tap="pickLength(n)"
        >{{ n }} 字</text>
      </view>
    </view>

    <view class="card">
      <text class="card-title">保存聊天记录</text>
      <view class="row">
        <text class="muted">记录仅保存在本机数据库，用于回看生成历史</text>
        <switch :checked="settings.saveHistory" color="#7C5CFF" @change="toggleHistory" />
      </view>
    </view>
  </view>
</template>

<script>
import { getSettings, setStyle, setMaxReplyLength, setSaveHistory } from '../../utils/bridge'

export default {
  data() {
    return {
      settings: { defaultStyle: 'NATURAL', maxReplyLength: 30, saveHistory: true },
      styles: [
        { value: 'NATURAL', label: '🌿 自然' },
        { value: 'GENTLE', label: '❤️ 温柔' },
        { value: 'FUNNY', label: '😂 幽默' }
      ],
      lengths: [20, 30, 40]
    }
  },
  onShow() {
    this.settings = getSettings()
  },
  methods: {
    pickStyle(value) {
      setStyle(value)
      this.settings = getSettings()
    },
    pickLength(value) {
      setMaxReplyLength(value)
      this.settings = getSettings()
    },
    toggleHistory(e) {
      setSaveHistory(e.detail.value)
    }
  }
}
</script>

<style>
.segment {
  display: flex;
  flex-direction: row;
  margin-top: 8px;
}
.seg-item {
  flex: 1;
  text-align: center;
  padding: 10px 0;
  border-radius: 10px;
  margin-right: 8px;
  background-color: #2e2a4d;
  color: #f2f2f7;
  font-size: 14px;
}
.seg-item:last-child {
  margin-right: 0;
}
.seg-active {
  background-color: #7c5cff;
  color: #ffffff;
  font-weight: bold;
}
.row {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}
</style>
