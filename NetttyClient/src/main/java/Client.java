import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder;
import io.netty.handler.stream.ChunkedWriteHandler;
import java.io.File;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.*;

public class Client {
    public static void main(String[] args){
        Scanner scanner=new Scanner(System.in);
        FileClient fc=new FileClient("127.0.0.1",80);

        try {
            while (true) {
                String[] ops=scanner.nextLine().split(" ");
                int n= Integer.parseInt(ops[0]);
                String info = ops[1];
                if(n==-1)
                    break;
                fc.launchRequest(n,info);
            }
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            fc.close();
        }
    }
}

class FileClient{
    private EventLoopGroup worker;
    private Bootstrap bootstrap;
    private Channel channel;
    private HttpRequestMethods requestMethods;

    public FileClient(String host,int port){
        this.runClinet(host,port);
        this.requestMethods=new HttpRequestMethods();
    }

    private void runClinet(String host,int port) {
        this.worker=new NioEventLoopGroup();
        try {
            this.bootstrap = new Bootstrap();
            this.bootstrap.group(worker)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<NioSocketChannel>() {
                        @Override
                        protected void initChannel(NioSocketChannel ch) throws Exception {
                            ch.pipeline().addLast("encoder", new HttpRequestEncoder())
                                    .addLast("decoder", new HttpResponseDecoder())
                                    .addLast("Aggregator", new HttpObjectAggregator(65536))
                                    .addLast("chunkwriter",new ChunkedWriteHandler())
                                    .addLast("FileClientSend", new ClientSendHandler())
                                    .addLast("FileClientReceive", new ClientReceiveHandler());
                        }
                    })
                    .option(ChannelOption.SO_KEEPALIVE, Boolean.TRUE);
            ChannelFuture f = this.bootstrap.connect(host, port).sync();
            this.channel=f.channel();
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void launchRequest(int n, String filename) throws Exception{
        HttpRequest request;
        HttpPostRequestEncoder requestContent=null;

        if (n==0)
            request=this.requestMethods.queryFilesRequest();
        else if (n==1) {
            HttpPostRequestEncoder postEnc = this.requestMethods.postFileRequest(filename);
            request=postEnc.finalizeRequest();
            if(postEnc.isChunked())
                requestContent=postEnc;
        }
        else
            request=this.requestMethods.deleteFilesRequest(filename);

        this.channel.write(request);
        if(requestContent!=null)
            this.channel.write(requestContent);
        this.channel.flush();
    }

    public void close(){
        this.worker.shutdownGracefully();
    }
}

class ClientSendHandler extends ChannelOutboundHandlerAdapter{
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        assert msg instanceof HttpRequest;
        ctx.writeAndFlush(msg);
    }
}

class ClientReceiveHandler extends SimpleChannelInboundHandler<FullHttpResponse>{
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse fullRes) throws Exception {
        System.out.println(fullRes);
        byte[] bytes = new byte[fullRes.content().readableBytes()];
        fullRes.content().readBytes(bytes);
        String s= new String(bytes, Charset.defaultCharset());
        System.out.println(s);
        System.out.println("");

    }
}

class HttpRequestMethods {
    public String getUri(String queryPath, Map<String,String> queryParams){
        QueryStringEncoder queryEncoder = new QueryStringEncoder(queryPath);
        if(queryParams!=null){
            for (Map.Entry<String,String> ent: queryParams.entrySet()){
                queryEncoder.addParam(ent.getKey(),ent.getValue());
            }
        }
        return queryEncoder.toString();
    }

    public void setHeaders(HttpHeaders headers){
        headers.set(HttpHeaderNames.HOST,"localhost");
//        headers.set(HttpHeaderNames.CONNECTION,HttpHeaderValues.CLOSE);
        headers.set(HttpHeaderNames.ACCEPT_ENCODING,HttpHeaderValues.GZIP+","+HttpHeaderValues.DEFLATE);
        headers.set(HttpHeaderNames.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2");
        headers.set(HttpHeaderNames.USER_AGENT, "Netty Simple Http Client side");
        headers.set(HttpHeaderNames.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
    }

    public HttpRequest queryFilesRequest() throws Exception{
        Map<String,String> paras=new HashMap<>();
        paras.put("user","Muzelv");
        paras.put("password","123456");
        String uri=getUri("/queryAllFiles",paras);
        HttpRequest request=new DefaultHttpRequest(HttpVersion.HTTP_1_1,HttpMethod.GET, uri);
        setHeaders(request.headers());
//        request.headers().set(HttpHeaderNames.CONTENT_LENGTH,0);
        return request;
    }

    public HttpPostRequestEncoder postFileRequest(String fileName) throws Exception{
        String uri=getUri("/uploadFile",null);
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,HttpMethod.POST,uri);
        setHeaders(request.headers());
        HttpPostRequestEncoder postReqEnc= new HttpPostRequestEncoder(request,true);
        postReqEnc.addBodyAttribute("Author","Lvmuze");
        postReqEnc.addBodyAttribute("FileName",fileName);
        postReqEnc.addBodyFileUpload(fileName,new File("E:/"+fileName),HttpHeaderValues.TEXT_PLAIN.toString(),true);
        return postReqEnc;
    }

    public HttpRequest deleteFilesRequest(String fileName) throws Exception{
        Map<String,String> paras=new HashMap<>();
        paras.put("fileName",fileName);
        String uri=getUri("/delFile",paras);
        HttpRequest request=new DefaultHttpRequest(HttpVersion.HTTP_1_1,HttpMethod.DELETE, uri);
        setHeaders(request.headers());
        return request;
    }

    public HttpRequest putFileRequest(String fileName) throws Exception{
        String uri=getUri("/uploadFile",null);
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1,HttpMethod.PUT,uri);
        setHeaders(request.headers());
        HttpPostRequestEncoder postReqDec= new HttpPostRequestEncoder(request,true);
        postReqDec.addBodyAttribute("Author","Lvmuze");
        postReqDec.addBodyAttribute("FileName",fileName);
        postReqDec.addBodyFileUpload(fileName,new File("E:/"+fileName),HttpHeaderValues.TEXT_PLAIN.toString(),true);
        return request;
    }
}

