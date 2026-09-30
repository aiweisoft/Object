<template>
	<view class="content">
		<image class="logo" src="/static/logo.png"></image>
		<view class="text-area">
			<text class="title">{{title}}</text>
		</view>
		<button class="entry-btn" @click="goDetect('detect')">物体检测</button>
		<button class="entry-btn" @click="goDetect('face')">人脸检测</button>
	</view>
</template>

<script>
	export default {
		data() {
			return {
				title: 'Hello YOLO'
			}
		},
		onLoad() {

		},
		methods: {
			goDetect(type) {
				uni.navigateTo({
					url: '/pages/detect/detect?type=' + type
				})
			}
		}
	}
</script>

<style>
	.content {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
	}

	.logo {
		height: 200rpx;
		width: 200rpx;
		margin-top: 200rpx;
		margin-left: auto;
		margin-right: auto;
		margin-bottom: 50rpx;
	}

	.text-area {
		display: flex;
		justify-content: center;
	}

	.title {
		font-size: 36rpx;
		color: #8f8f94;
	}

	.entry-btn {
		margin-top: 60rpx;
		width: 400rpx;
		background-color: #007aff;
		color: #ffffff;
		border-radius: 12rpx;
		font-size: 30rpx;
	}
</style>
