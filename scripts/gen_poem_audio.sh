#!/bin/bash
# 生成三上必背古诗/课文的朗读（macOS，Tingting 中文语音）：res/raw/poem_*.m4a
# 多音字用同音字替换喂给 TTS（显示文本不动，见 Grade3Recitation.annotate 的注音表）：
#   朝(zhāo)→招  还(huán)→环  重(chóng)→虫  柴(zhài)→寨  挑(tiǎo)→窕
#   没(mò)→莫    查(zhā)→扎   曲(qǔ)→取     扇走(shān)→煽走
set -e
cd "$(dirname "$0")/.."
RAW=app/src/main/res/raw
TMP=$(mktemp -d)
P="[[slnc 400]]"

# slug|朗读文本（已做同音字替换）
ITEMS=(
  "suo_jian|《所见》，清，袁枚。$P 牧童骑黄牛，$P 歌声振林樾。$P 意欲捕鸣蝉，$P 忽然闭口立。"
  "wang_dongting|《望洞庭》，唐，刘禹锡。$P 湖光秋月两相和，$P 潭面无风镜未磨。$P 遥望洞庭山水翠，$P 白银盘里一青螺。"
  "shan_xing|《山行》，唐，杜牧。$P 远上寒山石径斜，$P 白云生处有人家。$P 停车坐爱枫林晚，$P 霜叶红于二月花。"
  "ye_shu_suo_jian|《夜书所见》，宋，叶绍翁。$P 萧萧梧叶送寒声，$P 江上秋风动客情。$P 知有儿童窕促织，$P 夜深篱落一灯明。"
  "zhou_ye_shu_suo_jian|《舟夜书所见》，清，扎慎行。$P 月黑见渔灯，$P 孤光一点萤。$P 微微风簇浪，$P 散作满河星。"
  "zao_fa_bai_di|《早发白帝城》，唐，李白。$P 招辞白帝彩云间，$P 千里江陵一日环。$P 两岸猿声啼不住，$P 轻舟已过万虫山。"
  "lu_zhai|《鹿寨》，唐，王维。$P 空山不见人，$P 但闻人语响。$P 返景入深林，$P 复照青苔上。"
  "wang_tian_men_shan|《望天门山》，唐，李白。$P 天门中断楚江开，$P 碧水东流至此回。$P 两岸青山相对出，$P 孤帆一片日边来。"
  "yin_hu_shang|《饮湖上初晴后雨》，宋，苏轼。$P 水光潋滟晴方好，$P 山色空蒙雨亦奇。$P 欲把西湖比西子，$P 淡妆浓抹总相宜。"
  "cai_lian_qu|《采莲取》，唐，王昌龄。$P 荷叶罗裙一色裁，$P 芙蓉向脸两边开。$P 乱入池中看不见，$P 闻歌始觉有人来。"
  "si_ma_guang|《司马光》。$P 群儿戏于庭，$P 一儿登瓮，足跌莫水中。$P 众皆弃去，$P 光持石击瓮破之，$P 水迸，儿得活。"
  "qiu_tian_de_yu|《秋天的雨》节选。$P 秋天的雨，有一盒五彩缤纷的颜料。$P 它把黄色给了银杏树，黄黄的叶子像一把把小扇子，煽走了夏天的炎热。$P 它把红色给了枫树，红红的枫叶像一枚枚邮票，邮来了秋天的凉爽。$P 田野像金色的海洋。"
  "da_zi_ran_de_sheng_yin|《大自然的声音》节选。$P 风，是大自然的音乐家。$P 他会在森林里演奏他的手风琴。$P 水，也是大自然的音乐家。$P 下雨的时候，他喜欢玩打击乐器，$P 小雨滴敲敲打打，一场热闹的音乐会便开始了。"
)

for item in "${ITEMS[@]}"; do
    slug="${item%%|*}"
    text="${item#*|}"
    say -v Tingting -r 150 -o "$TMP/p.aiff" "[[slnc 200]] $text [[slnc 300]]"
    afconvert -f m4af -d aac@44100 -b 96000 --src-quality 127 "$TMP/p.aiff" "$RAW/poem_$slug.m4a"
    printf "  %-24s %ss\n" "$slug" "$(afinfo "$RAW/poem_$slug.m4a" | grep 'estimated duration' | awk '{print $3}')"
done
rm -rf "$TMP"
echo "完成：$(ls "$RAW"/poem_*.m4a | wc -l | tr -d ' ') 段朗读"
