<template>
	<view class="container">
		<view class="title">{{ type === 'face' ? '人脸检测' : '物体检测' }}</view>
		<view class="subtitle">{{ type === 'face' ? '基于 YOLOv8 的本地离线实时人脸检测' : '基于 YOLOv8 的本地离线实时目标检测（COCO 80 类）' }}</view>

		<view class="card">
			<text class="card-title">实时检测</text>
			<text class="card-desc">打开手机摄像头，实时识别画面中的物体</text>
			<button class="btn btn-primary" @click="startRealtime">启动摄像头检测</button>
		</view>

		<view class="card">
			<text class="card-title">图片检测</text>
			<text class="card-desc">从相册选择一张图片进行识别</text>
			<button class="btn btn-primary" @click="chooseAndDetect">选择图片检测</button>
			<image v-if="imageSrc" :src="imageSrc" class="preview" mode="aspectFit"></image>
		</view>

		<view class="card">
			<text class="card-title">检测结果（{{ results.length }}）</text>
			<view v-if="results.length === 0" class="empty">
				<text class="empty-text">暂无检测结果</text>
			</view>
			<view v-for="(r, i) in results" :key="i" class="result-item">
				<text class="result-name">{{ r.className }}</text>
				<text class="result-score">{{ formatScore(r.score) }}</text>
			</view>
		</view>

		<view class="tip">
			<text class="tip-text">提示：实时检测为原生界面，检测框直接绘制在画面上；关闭后回到本页查看最近一次结果。</text>
		</view>
	</view>
</template>

<script>
// #ifdef APP-PLUS
import { detect, startDetect, stopDetect } from "@/uni_modules/uni-yolo-detect"
// #endif

export default {
	data() {
		return {
			results: [],
			imageSrc: '',
			type: 'detect'
		}
	},
	onLoad(options) {
		if (options && options.type) {
			this.type = options.type
		}
	},
	onUnload() {
		// #ifdef APP-PLUS
		try { stopDetect() } catch (e) {}
		// #endif
	},
	methods: {
		startRealtime() {
			// #ifdef APP-PLUS
			if (this.isAndroid()) {
				startDetect({
					threshold: 0.25,
					type: this.type,
					onResult: (res) => {
						this.results = res
					},
					success: () => {
						console.log('检测已启动')
					},
					fail: (err) => {
						uni.showToast({ title: (err && err.errMsg) || '启动失败', icon: 'none' })
					}
				})
			} else {
				uni.showToast({ title: '实时检测仅支持 Android 端', icon: 'none' })
			}
			// #endif
			// #ifndef APP-PLUS
			uni.showToast({ title: '实时检测仅支持 App 端', icon: 'none' })
			// #endif
		},
		chooseAndDetect() {
			uni.chooseImage({
				count: 1,
				success: (res) => {
					const path = res.tempFilePaths[0]
					this.imageSrc = path
					// #ifdef APP-PLUS
					if (this.isAndroid()) {
						detect({
							imagePath: path,
							type: this.type,
							success: (results) => {
								this.results = results
							},
							fail: (err) => {
								uni.showToast({ title: (err && err.errMsg) || '检测失败', icon: 'none' })
							}
						})
					} else {
						uni.showToast({ title: '图片检测仅支持 Android 端', icon: 'none' })
					}
					// #endif
					// #ifndef APP-PLUS
					uni.showToast({ title: '图片检测仅支持 App 端', icon: 'none' })
					// #endif
				}
			})
		},
		isAndroid() {
			try {
				return uni.getSystemInfoSync().platform === 'android'
			} catch (e) {
				return false
			}
		},
		formatScore(score) {
			return (Math.round(score * 100) / 100).toFixed(2)
		}
	}
}
</script>

<style>
.container {
	min-height: 100vh;
	background-color: #f5f6f8;
	padding: 40rpx;
	box-sizing: border-box;
}

.title {
	font-size: 44rpx;
	font-weight: bold;
	color: #1a1a1a;
}

.subtitle {
	font-size: 24rpx;
	color: #888;
	margin-top: 12rpx;
	margin-bottom: 32rpx;
}

.card {
	background-color: #fff;
	border-radius: 16rpx;
	padding: 32rpx;
	margin-bottom: 24rpx;
	box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}

.card-title {
	font-size: 30rpx;
	font-weight: bold;
	color: #333;
}

.card-desc {
	display: block;
	font-size: 24rpx;
	color: #999;
	margin-top: 8rpx;
	margin-bottom: 24rpx;
}

.btn {
	border-radius: 12rpx;
	font-size: 30rpx;
	line-height: 2.4;
}

.btn-primary {
	background-color: #007aff;
	color: #fff;
}

.preview {
	width: 100%;
	height: 400rpx;
	margin-top: 24rpx;
	border-radius: 8rpx;
	background-color: #f0f0f0;
}

.empty {
	padding: 40rpx 0;
	text-align: center;
}

.empty-text {
	font-size: 26rpx;
	color: #bbb;
}

.result-item {
	display: flex;
	justify-content: space-between;
	align-items: center;
	padding: 20rpx 0;
	border-bottom: 1rpx solid #f0f0f0;
}

.result-name {
	font-size: 30rpx;
	color: #333;
}

.result-score {
	font-size: 28rpx;
	color: #007aff;
	font-weight: bold;
}

.tip {
	margin-top: 16rpx;
}

.tip-text {
	font-size: 22rpx;
	color: #aaa;
	line-height: 1.6;
}
</style>
