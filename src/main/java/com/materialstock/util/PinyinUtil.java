package com.materialstock.util;

import java.util.HashMap;
import java.util.Map;

/**
 * 内置汉字→拼音映射（无外部依赖，离线可用）。
 * 覆盖《我的世界》1.21 物品/方块中文名的常用字；
 * 未覆盖的汉字在拼音转换时忽略（不影响中文名/ID 的原匹配）。
 * 支持：全拼匹配（huoba）、首字母匹配（hb）。
 */
public final class PinyinUtil {
    private static final Map<Character, String> PY = new HashMap<>();

    static {
        String data = "石=shi,木=mu,土=tu,砂=sha,砾=li,黏=nian,粘=zhan,砖=zhuan,陶=tao,瓦=wa,玻=bo,璃=li,"
            + "铁=tie,金=jin,铜=tong,银=yin,钻=zuan,玉=yu,宝=bao,珍=zhen,珠=zhu,翡=fei,翠=cui,煤=mei,炭=tan,"
            + "硝=xiao,硫=liu,磷=lin,盐=yan,灰=hui,粉=fen,锭=ding,块=kuai,粒=li,矿=kuang,深=shen,层=ceng,"
            + "粗=cu,生=sheng,熟=shu,原=yuan,下=xia,界=jie,合=he,残=can,骸=hai,远=yuan,古=gu,英=ying,绿=lv,"
            + "青=qing,红=hong,荧=ying,海=hai,晶=jing,绵=mian,湿=shi,干=gan,珊=shan,瑚=hu,紫=zi,颂=song,"
            + "果=guo,末=mo,影=ying,眼=yan,鞘=qiao,翅=chi,幻=huan,翼=yi,膜=mo,鳞=lin,甲=jia,鹦=ying,鹉=wu,"
            + "螺=luo,贝=bei,壳=ke,龟=gui,潮=chao,汐=xi,守=shou,卫=wei,者=zhe,烈=lie,焰=yan,棒=bang,恶=e,"
            + "魂=hun,之=zhi,泪=lei,岩=yan,浆=jiang,膏=gao,幽=you,匿=ni,感=gan,体=ti,源=yuan,催=cui,哭=ku,"
            + "咬=yao,嗡=weng,尖=jian,吼=hou,瘀=yu,斑=ban,苔=tai,藓=xian,菌=jun,孢=bao,真=zhen,绯=fei,"
            + "诡=gui,扭=niu,曲=qu,垂=chui,藤=teng,缠=chan,怨=yuan,发=fa,光=guang,的=de,疣=you,丝=si,"
            + "柄=bing,核=he,盖=gai,皮=pi,橡=xiang,云=yun,杉=shan,桦=hua,丛=cong,林=lin,色=se,树=shu,"
            + "樱=ying,花=hua,去=qu,板=ban,台=tai,阶=jie,楼=lou,梯=ti,栅=zha,栏=lan,门=men,活=huo,告=gao,"
            + "示=shi,牌=pai,悬=xuan,挂=gua,苗=miao,叶=ye,棍=gun,锤=chui,盾=dun,工=gong,具=ju,斧=fu,镐=gao,"
            + "锄=chu,锹=qiao,铲=chan,剪=jian,钳=qian,锯=ju,刷=shua,剑=jian,刀=dao,弓=gong,弩=nu,箭=jian,"
            + "盔=kui,胸=xiong,护=hu,腿=tui,靴=xue,帽=mao,衣=yi,裤=ku,衫=shan,马=ma,铠=kai,鞍=an,缰=jiang,"
            + "绳=sheng,拴=shuan,铃=ling,铛=dang,图=tu,腾=teng,不=bu,死=si,徽=hui,章=zhang,战=zhan,利=li,"
            + "品=pin,潜=qian,盒=he,收=shou,纳=na,桶=tong,发=fa,射=she,器=qi,投=tou,掷=zhi,观=guan,察=cha,"
            + "塞=sai,性=xing,液=ye,蜂=feng,蜜=mi,脾=pi,巢=chao,箱=xiang,蜡=la,烛=zhu,蛋=dan,糕=gao,奇=qi,"
            + "面=mian,包=bao,馅=xian,饼=bing,派=pai,汤=tang,炖=dun,煲=bao,蘑=mo,菇=gu,兔=tu,肉=rou,牛=niu,"
            + "排=pai,鸡=ji,羊=yang,腐=fu,蜘=zhi,蛛=zhu,苹=ping,胡=hu,萝=luo,卜=bo,铃=ling,薯=shu,甜=tian,"
            + "菜=cai,根=gen,南=nan,瓜=gua,西=xi,奶=nai,水=shui,药=yao,喷=pen,溅=jian,滞=zhi,留=liu,疗=liao,"
            + "伤=shang,害=hai,隐=yin,身=shen,夜=ye,视=shi,呼=hu,吸=xi,防=fang,火=huo,抗=kang,力=li,量=liang,"
            + "迅=xun,捷=jie,缓=huan,慢=man,虚=xu,弱=ruo,剧=ju,毒=du,跳=tiao,跃=yue,迟=chi,降=jiang,寄=ji,"
            + "盘=pan,渗=shen,败=bai,淤=yu,积=ji,孽=nie,预=yu,兆=zhao,祥=xiang,试=shi,炼=lian,通=tong,风=feng,"
            + "匠=jiang,造=zao,吹=chui,笛=di,手=shou,袭=xi,村=cun,庄=zhuang,掠=lve,夺=duo,厄=e,运=yun,商=shang,"
            + "队=dui,驼=tuo,骆=luo,驴=lv,骡=luo,骷=ku,髅=lou,僵=jiang,尸=shi,苦=ku,怕=pa,洞=dong,穴=xue,"
            + "凋=diao,灵=ling,蛮=man,兵=bing,兽=shou,炽=chi,足=zu,民=min,流=liu,浪=lang,傀=kui,儡=lei,雪=xue,"
            + "女=nv,巫=wu,术=shu,师=shi,道=dao,唤=huan,魔=mo,恼=nao,鬼=gui,溺=ni,鳕=xue,鱼=yu,鲑=gui,热=re,"
            + "带=dai,河=he,豚=tun,鱿=you,蝾=rong,螈=yuan,蛙=wa,蝌=ke,蚪=dou,猫=mao,狗=gou,狼=lang,狐=hu,"
            + "熊=xiong,北=bei,极=ji,蠹=du,虫=chong,蛞=kuo,蝓=yu,史=shi,莱=lai,姆=mu,龙=long,蝙=bian,蝠=fu,"
            + "哞=mou,嗅=xiu,人=ren,陷=xian,阱=jing,混=hun,凝=ning,涂=tu,上=shang,染=ran,白=bai,橙=cheng,"
            + "洋=yang,淡=dan,黄=huang,蓝=lan,棕=zong,黑=hei,浅=qian,平=ping,滑=hua,磨=mo,制=zhi,切=qie,"
            + "凿=zao,裂=lie,纹=wen,墙=qiang,柱=zhu,头=tou,颅=lu,骨=gu,雕=diao,刻=ke,灯=deng,笼=long,篝=gou,"
            + "营=ying,把=ba,安=an,闪=shan,长=chang,岗=gang,凝=ning,方=fang,解=jie,玄=xuan,武=wu,抛=pao,"
            + "烁=shuo,錾=zan,芽=ya,簇=cu,母=mu,蕴=yun,育=yu,舍=she,罐=guan,饰=shi,铭=ming,碎=sui,片=pian,"
            + "袋=dai,漏=lou,斗=dou,侦=zhen,测=ce,拉=la,杆=gan,按=an,钮=niu,压=ya,踏=ta,中=zhong,继=ji,比=bi,"
            + "较=jiao,音=yin,符=fu,唱=chang,留=liu,声=sheng,机=ji,讲=jiang,织=zhi,布=bu,图=tu,锻=duan,轮=lun,"
            + "打=da,燧=sui,砧=zhen,动=dong,轨=gui,探=tan,激=ji,充=chong,能=neng,普=pu,路=lu,车=che,运=yun,"
            + "输=shu,命=ming,令=ling,船=chuan,竹=zhu,筏=fa,烟=yan,旗=qi,帜=zhi,毛=mao,毯=tan,床=chuang,"
            + "被=bei,画=hua,展=zhan,框=kuang,架=jia,盆=pen,草=cao,蕨=jue,灌=guan,浆=jiang,萤=ying,球=qiu,"
            + "爆=bao,植=zhi,株=zhu,粗=cu,捆=kun,脚=jiao,手=shou,传=chuan,送=song,重=chong,锚=mao,磁=ci,"
            + "避=bi,雷=lei,针=zhen,格=ge,氧=yang,锈=xiu,蚀=shi,风=feng,化=hua,熄=xi,灭=mie,燃=ran,烧=shao,"
            + "点=dian,成=cheng,配=pei,熔=rong,冶=ye,割=ge,作=zuo,桌=zhuo,椅=yi,凳=deng,柜=gui,锅=guo,碗=wan,"
            + "勺=shao,筷=kuai,杯=bei,壶=hu,裙=qun,背=bei,心=xin,外=wai,鞋=xie,袜=wa,子=zi,纽=niu,扣=kou,"
            + "链=lian,口=kou,棉=mian,绸=chou,纱=sha,呢=ni,麻=ma,纤=xian,维=wei,革=ge,羽=yu,绒=rong,角=jiao,"
            + "蹄=ti,尾=wei,爪=zhua,触=chu,耳=er,鼻=bi,嘴=zui,牙=ya,舌=she,喉=hou,颈=jing,肩=jian,臂=bi,"
            + "肘=zhou,腕=wan,掌=zhang,指=zhi,膝=xi,踝=huai,血=xie,髓=sui,筋=jin,腱=jian,细=xi,胞=bao,组=zu,"
            + "器=qi,系=xi,统=tong,矿=kuang,物=wu,品=pin,用=yong,可=ke,合=he,搜=sou,索=suo,收=shou,藏=cang,"
            + "备=bei,货=huo,区=qu,材=cai,料=liao,清=qing,空=kong,选=xuan,择=ze,成=cheng,基=ji,础=chu,默=mo,"
            + "认=ren,推=tui,荐=jian,数=shu,倍=bei,加=jia,入=ru,删=shan,除=chu,退=tui,出=chu,保=bao,存=cun,"
            + "列=lie,表=biao,搜=sou,滚=gun,页=ye,翻=fan,显=xian,隐=yin,开=kai,关=guan,改=gai,名=ming,确=que,"
            + "定=ding,取=qu,消=xiao,扫=sao,需=xu,要=yao,已=yi,有=you,缺=que,少=shao,对=dui,"
            + "比=bi,差=cha,足=zu,够=gou,无=wu,法=fa,请=qing,先=xian,再=zai,后=hou,新=xin,旧=jiu,当=dang,前=qian,"
            + "共=gong,个=ge,种=zhong,容=rong,器=qi,缓=huan,存=cun,投=tou,影=ying,文=wen,件=jian,目=mu,录=lu,"
            + "错=cuo,误=wu,失=shi,败=bai,成=cheng,功=gong,更=geng,新=xin,正=zheng,常=chang,暂=zan,时=shi,"
            + "候=hou,等=deng,待=dai,完=wan,全=quan,部=bu,分=fen,数=shu,量=liang,总=zong,计=ji,剩=sheng,余=yu,"
            + "还=hai,再=zai,继=ji,续=xu,挂=gua,悬=xuan,招=zhao,架=jia,旗=qi,图=tu,墙=qiang,地=di,板=ban,"
            + "屋=wu,顶=ding,底=di,部=bu,侧=ce,旁=pang,边=bian,上=shang,中=zhong,间=jian,左=zuo,右=you,正=zheng,一=yi,七=qi,万=wan,三=san,与=yu,丐=gai,世=shi,业=ye,为=wei,主=zhu,九=jiu,于=yu,五=wu,交=jiao,产=chan,仓=cang,仙=xian,代=dai,优=you,伪=wei,使=shi,供=gong,信=xin,修=xiu,像=xiang,党=dang,六=liu,内=nei,农=nong,冰=bing,凋=diao,别=bie,务=wu,千=qian,半=ban,卸=xie,双=shuang,叠=die,吊=diao,向=xiang,和=he,哥=ge,四=si,园=yuan,围=wei,固=gu,圆=yuan,圣=sheng,场=chang,城=cheng,堆=dui,堡=bao,塔=ta,塞=sai,填=tian,境=jing,墅=shu,处=chu,复=fu,大=da,天=tian,套=tao,女=nv,好=hao,字=zi,宗=zong,宫=gong,密=mi,尺=chi,屋=wu,屠=tu,岛=dao,岸=an,巧=qiao,巨=ju,巴=ba,带=dai,年=nian,库=ku,座=zuo,建=jian,式=shi,引=yin,彩=cai,彼=bi,律=lv,德=de,怪=guai,情=qing,打=da,折=zhe,挡=dang,换=huan,排=pai,接=jie,效=xiao,教=jiao,施=shi,旋=xuan,易=yi,星=xing,暗=an,最=zui,月=yue,服=fu,期=qi,杀=sha,标=biao,栉=zhi,桃=tao,樱=ying,橘=ju,欢=huan,款=kuan,殖=zhi,殿=dian,沙=sha,沼=zhao,泉=quan,泡=pao,泥=ni,泽=ze,洛=luo,洲=zhou,浮=fu,滕=teng,满=man,滴=di,炉=lu,炮=pao,炸=zha,版=ban,特=te,狱=yu,猪=zhu,率=lv,王=wang,玫=mei,珞=luo,瑰=gui,璎=ying,甘=gan,电=dian,百=bai,盗=dao,盘=pan,神=shen,秋=qiu,站=zhan,童=tong,端=duan,筑=zhu,简=jian,糖=tang,繁=fan,级=ji,终=zhong,缝=feng,群=qun,肥=fei,脏=zang,荒=huang,菜=cai,葭=jia,蒙=meng,蒹=jian,蔗=zhe,虹=hong,街=jie,装=zhuang,设=she,谷=gu,豆=dou,赞=zan,超=chao,转=zhuan,轰=hong,载=zai,进=jin,适=shi,速=su,酒=jiu,钟=zhong,铁=tie,锥=zhui,镇=zhen,镜=jing,阁=ge,附=fu,院=yuan,雅=ya,集=ji,零=ling,非=fei,项=xiang,馆=guan,骗=pian,高=gao,魂=hun,鲸=jing,鸣=ming,鹅=e,鹿=lu,麦=mai,黎=li,单=dan,十=shi,备=bei,小=xiao,士=shi,段=duan,候=hou,还=hai,"
            // 补字：按物品名汉字频次补全（原表 895 字 -> 1096 字，覆盖物品名字符出现量的 100%）
            + "条=tiao,形=xing,竖=shu,型=xing,斜=xie,波=bo,横=heng,样=yang,栽=zai,自=zi,渐=jian,异=yi,"
            + "扇=shan,模=mo,乐=le,涡=wo,驳=bo,朵=duo,插=cha,菱=ling,釉=you,气=qi,瓶=ping,山=shan,"
            + "香=xiang,脑=nao,管=guan,郁=yu,案=an,书=shu,杜=du,鹃=juan,美=mei,或=huo,锁=suo,茎=jing,"
            + "在=zai,钓=diao,状=zhuang,结=jie,你=ni,兰=lan,酿=niang,菊=ju,珀=po,线=xian,治=zhi,飘=piao,"
            + "幸=xing,肺=fei,蓄=xu,放=fang,赛=sai,克=ke,息=xi,蔓=man,盛=sheng,明=ming,举=ju,竿=gan,"
            + "弹=dan,凡=fan,浓=nong,稠=chou,葱=cong,现=xian,休=xiu,这=zhe,张=zhang,阻=zu,矢=shi,曜=yao,"
            + "蒲=pu,公=gong,枯=ku,萎=wei,首=shou,质=zhi,胎=tai,滨=bin,玩=wan,家=jia,虞=yu,置=zhi,"
            + "构=gou,疑=yi,绊=ban,犰=qiu,狳=yu,刃=ren,佳=jia,危=wei,险=xian,行=xing,知=zhi,挚=zhi,"
            + "友=you,墨=mo,囊=nang,爱=ai,嚎=hao,采=cai,悲=bei,恸=tong,钥=yao,匙=chi,富=fu,饶=rao,"
            + "荫=yin,铸=zhu,笋=sun,屏=ping,障=zhang,辅=fu,助=zhu,只=zhi,暴=bao,眠=mian,周=zhou,游=you,"
            + "荡=dang,占=zhan,太=tai,了=le,校=xiao,频=pin,连=lian,网=wang,涌=yong,泣=qi,损=sun,坏=huai,"
            + "阳=yang,径=jing,耕=geng,卵=luan,霜=shuang,镶=xiang,沉=chen,拼=pin,轻=qing,丁=ding,睡=shui,莲=lian,"
            + "覆=fu,移=yi,沾=zhan,赭=zhe,坯=pi,牡=mu,丹=dan,强=qiang,循=xun,环=huan,啸=xiao,脉=mai,"
            + "络=luo,矮=ai,熏=xun,互=hu,类=lei,位=wei,日=ri,葵=kui,靶=ba,遮=zhe,钩=gou,悦=yue,"
            + "烤=kao,调=diao,属=shu,回=hui,响=xiang,螨=man,酵=jiao,飞=fei,义=yi,至=zhi,未=wei,踪=zong,"
            + "迹=ji,狸=li,识=shi,八=ba,豹=bao,纸=zhi,荚=jia,劫=jie,追=zhui,溯=su,刮=gua,削=xiao,"
            + "应=ying,所=suo,升=sheng,望=wang,谜=mi,叉=cha,戟=ji,监=jian,笔=bi,"
            // 界面状态词用字（搜索框提示"忽略/缺失/完成"）
            // 略=lve 与表中已有的 掠=lve 保持一致（UE 输入法写作 lue，但表内统一用 lve）
            + "忽=hu,略=lve,";
        for (String e : data.split(",")) {
            int i = e.indexOf('=');
            if (i > 0 && i < e.length() - 1) {
                PY.put(e.charAt(0), e.substring(i + 1));
            }
        }
    }

    private PinyinUtil() {
    }

    /** 中文转全拼（未覆盖的汉字忽略，只拼接已覆盖字的拼音） */
    public static String toFull(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            String p = PY.get(s.charAt(i));
            if (p != null) sb.append(p);
        }
        return sb.toString();
    }

    /** 中文转拼音首字母（未覆盖的汉字忽略） */
    public static String toInitials(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            String p = PY.get(s.charAt(i));
            if (p != null && !p.isEmpty()) sb.append(p.charAt(0));
        }
        return sb.toString();
    }
}
