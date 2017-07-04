package com.service.handler;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpUtil.isKeepAlive;
import static io.netty.handler.codec.http.HttpUtil.setContentLength;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * @creator ZTH
 * @modifier ZTH
 * @date 2017-07-03
 */
public class WebSocketServerHandler extends SimpleChannelInboundHandler<Object> {
 	private static final Logger LOGGER = LoggerFactory
			.getLogger(WebSocketServerHandler.class);

	//这个map用于保存userId和SocketChannel的对应关系
	public static Map<String,io.netty.channel.Channel>  userChannel =new ConcurrentHashMap();

	private WebSocketServerHandshaker handshaker;

	protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
		LOGGER.info("channel read start now!");
		// 传统的HTTP接入
		if (msg instanceof FullHttpRequest) {
			LOGGER.info("channel read FullHttpRequest now!");
			handleHttpRequest(ctx, (FullHttpRequest) msg);
		}
		// WebSocket接入
		else if (msg instanceof WebSocketFrame) {
			LOGGER.info("channel read WebSocketFrame now!");
			handleWebSocketFrame(ctx, (WebSocketFrame) msg);
		}
	}

	private void handleHttpRequest(ChannelHandlerContext ctx,
								   FullHttpRequest req) throws Exception {

		// 如果HTTP解码失败，返回HHTP异常
		if (!req.getDecoderResult().isSuccess()
				|| (!"websocket".equals(req.headers().get("Upgrade")))) {
			/*DecoderResult decoderResult = req.decoderResult();
			if (decoderResult.isFinished()){
				System.out.println("decoderResult.toString() = " + decoderResult.toString());
			}*/
			sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1,
					BAD_REQUEST));
			return;
		}else {
			QueryStringDecoder decoder = new QueryStringDecoder(req.getUri());
			String path = decoder.path();
			System.out.println("path = " + path);
			Map<String, List<String>> params = decoder.parameters();
			List<String> userIds = params.get("userId"); // 读取从客户端传过来的参数
			if (userIds!=null && userIds.size()>0){
				final String userId = userIds.get(0);
				LOGGER.info("userId = "+userId);
				io.netty.channel.Channel channel = ctx.channel();
				userChannel.put(userId,channel);
				LOGGER.info("channel = "+channel);
				Thread thread = new Thread(new Runnable() {
					public void run() {
						try {
							LOGGER.info("sleeping now()");
							Thread.sleep(5000);
							LOGGER.info("after sleep 5 seconds");
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						Channel targetChannel = userChannel.get(userId);
						String msg = "push test!";
						LOGGER.info("the target channel to be push is :{}", targetChannel);
						targetChannel.writeAndFlush(new TextWebSocketFrame(msg));
					}
				});
				thread.start();
			}
		}

		// 构造握手响应返回，本机测试
		WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
				"ws://localhost:8080/websocket", null, false);
		handshaker = wsFactory.newHandshaker(req);
		if (handshaker == null) {
			WebSocketServerHandshakerFactory
					.sendUnsupportedVersionResponse(ctx.channel());
		} else {
			handshaker.handshake(ctx.channel(), req);
		}
	}

	private void handleWebSocketFrame(ChannelHandlerContext ctx,
									  WebSocketFrame frame) {

		// 判断是否是关闭链路的指令
		if (frame instanceof CloseWebSocketFrame) {
			handshaker.close(ctx.channel(),
					(CloseWebSocketFrame) frame.retain());
			return;
		}
		// 判断是否是Ping消息
		if (frame instanceof PingWebSocketFrame) {
			ctx.channel().write(
					new PongWebSocketFrame(frame.content().retain()));
			return;
		}
		// 本例程仅支持文本消息，不支持二进制消息
		if (!(frame instanceof TextWebSocketFrame)) {
			throw new UnsupportedOperationException(String.format(
					"%s frame types not supported", frame.getClass().getName()));
		}

		// 返回应答消息
		String request = ((TextWebSocketFrame) frame).text();
		/*if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.fine(String.format("%s received %s", ctx.channel(), request));
		}*/
		LOGGER.info("channel:{} received request:{}",ctx.channel(), request);
		ctx.channel().write(new TextWebSocketFrame(" 收到客户端请求："+request));
	}

	private static void sendHttpResponse(ChannelHandlerContext ctx,
										 FullHttpRequest req, FullHttpResponse res) {
		// 返回应答给客户端
		if (res.getStatus().code() != 200) {
			ByteBuf buf = Unpooled.copiedBuffer(res.getStatus().toString(),
					CharsetUtil.UTF_8);
			res.content().writeBytes(buf);
			buf.release();
			setContentLength(res, res.content().readableBytes());
		}

		// 如果是非Keep-Alive，关闭连接
		ChannelFuture f = ctx.channel().writeAndFlush(res);
		if (!isKeepAlive(req) || res.getStatus().code() != 200) {
			f.addListener(ChannelFutureListener.CLOSE);
		}
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
		ctx.flush();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
			throws Exception {
		cause.printStackTrace();
		ctx.close();
	}
}
