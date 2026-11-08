package org.telegram.messenger;

/**
 * Simplified Chinese to Traditional, one character at a time.
 * Applied on the device so 简体 / 繁體 / English do not wait for langpack.
 */
public final class TellomiChineseScript {

    private TellomiChineseScript() {
    }

    private static final String SIMP =
        "万与专业东丢两个丰临为举么义乌乐书买争于云亚产亲亿仅从们价众优伙会传伦体余佣侦侧储儿兑兰关兴兹内册写决况冻准减凑几凯击划" +
        "则刚创删别办务动励势勋区协单卖占卫却历压厦参双发变叠台号后吗听启员响哟团围国图圆场坏块墙声处备复够头夹夺奖学宁宝实审宽对" +
        "寻导将尔尘尝尽属岁岛币师帐帜带帧帮并广庄庆库应废开弃张弹强归当录彻征径忆态总悬愿戏战户托执扩扫扬扰抢护报担拟拥拦拨择挂挝" +
        "损换据掷携摄摇数断无旧时昵显晕暂术机杂权条来极构栅标栏样档检槛欢毁毕气汇汉没泄浅测济浏涂润涨渐温游溃滚满滤滥滨潜灯灵点烁" +
        "热爱状独环现电画畅盖盗盘着矶码础确礼离种积称窝窥竞笔筛签简类紧红约级纯纳纸纹纽线组细织终绍经绑结给络绝统继绪续维绿缅缓编" +
        "缘缝缩网罗职联胜脑脱脸腊腾艺节苏范荐药莱获营萨蓝虑虚衔衬装见观规视览觉触计订认讨让议讯记讲许论设访证评识诈诉词译试诗话询" +
        "该详语误说请诺读谁调谈谚谢谨谱贝负责败账货质购贴贵费资赏赚赞赠赢跃踪车转轮软轻载较辅辑输边达迁过迈运还这进远违连迟迹适选" +
        "遗邮郑酱采释里针钟钥钮钱铃银链销锁锐错锡键镜长门闪闭问闲间闻阅阈队阳阴际险随隐静韩页顶项顺须顾顿预领频颗题颜额风飞饮饰饱" +
        "馈马驶驻驾验骗骤鲁鲍鸦麦黄齐龄" +
        "";

    private static final String TRAD =
        "萬與專業東丟兩個豐臨為舉麼義烏樂書買爭於雲亞產親億僅從們價眾優夥會傳倫體餘傭偵側儲兒兌蘭關興茲內冊寫決況凍準減湊幾凱擊劃" +
        "則剛創刪別辦務動勵勢勳區協單賣佔衛卻歷壓廈參雙發變疊臺號後嗎聽啟員響喲團圍國圖圓場壞塊牆聲處備復夠頭夾奪獎學寧寶實審寬對" +
        "尋導將爾塵嘗盡屬歲島幣師帳幟帶幀幫並廣莊慶庫應廢開棄張彈強歸當錄徹徵徑憶態總懸願戲戰戶託執擴掃揚擾搶護報擔擬擁攔撥擇掛撾" +
        "損換據擲攜攝搖數斷無舊時暱顯暈暫術機雜權條來極構柵標欄樣檔檢檻歡毀畢氣匯漢沒洩淺測濟瀏塗潤漲漸溫遊潰滾滿濾濫濱潛燈靈點爍" +
        "熱愛狀獨環現電畫暢蓋盜盤著磯碼礎確禮離種積稱窩窺競筆篩籤簡類緊紅約級純納紙紋紐線組細織終紹經綁結給絡絕統繼緒續維綠緬緩編" +
        "緣縫縮網羅職聯勝腦脫臉臘騰藝節蘇範薦藥萊獲營薩藍慮虛銜襯裝見觀規視覽覺觸計訂認討讓議訊記講許論設訪證評識詐訴詞譯試詩話詢" +
        "該詳語誤說請諾讀誰調談諺謝謹譜貝負責敗賬貨質購貼貴費資賞賺贊贈贏躍蹤車轉輪軟輕載較輔輯輸邊達遷過邁運還這進遠違連遲跡適選" +
        "遺郵鄭醬採釋裡針鍾鑰鈕錢鈴銀鏈銷鎖銳錯錫鍵鏡長門閃閉問閒間聞閱閾隊陽陰際險隨隱靜韓頁頂項順須顧頓預領頻顆題顏額風飛飲飾飽" +
        "饋馬駛駐駕驗騙驟魯鮑鴉麥黃齊齡" +
        "";

    public static String toTraditional(String value) {
        if (value == null || value.length() == 0 || SIMP.length() != TRAD.length()) {
            return value;
        }
        StringBuilder sb = null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            int at = SIMP.indexOf(c);
            if (at >= 0) {
                if (sb == null) {
                    sb = new StringBuilder(value.length());
                    sb.append(value, 0, i);
                }
                sb.append(TRAD.charAt(at));
            } else if (sb != null) {
                sb.append(c);
            }
        }
        return sb == null ? value : sb.toString();
    }
}
