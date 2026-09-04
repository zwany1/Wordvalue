<template>
  <view class="page">
    <view class="hero">
      <text class="hero-title">高情商 AI 输入法</text>
      <text class="hero-sub">完全离线 · 数据不出手机</text>
    </view>

    <view class="card">
      <text class="card-title">输入法状态</text>
      <text :class="imeEnabled ? 'tag-ok' : 'tag-warn'">
        {{ imeEnabled ? '🟢 已启用' : '未启用，请到系统设置开启' }}
      </text>
    </view>

    <view class="card">
      <text class="card-title">当前模式</text>
      <text class="muted">{{ styleLabel(settings.defaultStyle) }}</text>
    </view>

    <view class="btn" @tap="goImeSettings">进入系统输入法设置</view>
    <view class="btn-soft" @tap="goHistory">聊天记录</view>

    <view class="card">
      <text class="card-title">测试 AI（原生端体验）</text>
      <input
        v-model="draft"
        class="input"
        placeholder="输入一句对方说的话，例如：今天好累啊"
        placeholder-class="input-ph"
      />
      <view class="btn" @tap="testGenerate">测试 AI</view>
      <view v-if="testNotice" class="muted">{{ testNotice }}</view>
    </view>
  </view>
</template>

<script>
import { getImeStatus, getSettings, openImeSettings } from '../../utils/bridge'

export default {
  data() {
    return {
      imeEnabled: false,
      settings: { defaultStyle: 'NATURAL' },
      draft: '',
      testNotice: ''
    }
  },
  onShow() {
    this.imeEnabled = getImeStatus().enabled === true
    this.settings = getSettings()
  },
  methods: {
    styleLabel(name) {
      const map = { NATURAL: '自然', GENTLE: '温柔', FUNNY: '幽默' }
      return map[name] || '自然'
    },
    goImeSettings() {
      openImeSettings()
    },
    goHistory() {
      uni.navigateTo({ url: '/pages/history/history' })
    },
    testGenerate() {
      if (!this.draft.trim()) {
        this.testNotice = '先输入一句对方的消息，然后复制它，在微信里点键盘的「AI 生成」即可体验完整流程'
        return
      }
      this.testNotice = '在任意输入框切换到高情商输入法，复制这句话后点击「AI 生成」'
    }
  }
}
</script>

<style>
.hero {
  padding: 30px 16px 10px;
  display: flex;
  flex-direction: column;
}
.hero-title {
  font-size: 26px;
  font-weight: bold;
  color: #ffffff;
}
.hero-sub {
  margin-top: 6px;
  font-size: 13px;
  color: #98989f;
}
.input {
  background-color: #1c1c1e;
  border-radius: 10px;
  padding: 10px 12px;
  margin: 10px 0;
  color: #f2f2f7;
  font-size: 14px;
}
.input-ph {
  color: #606066;
}
</style>
