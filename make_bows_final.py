from PIL import Image
import os

OUT = r'D:\gengxin\StarRailExpress\src\main\resources\assets\noellesroles\textures\item'

# Exact pixel masks from provided vanilla bow images
# B=bow body, G=grip(darker), S=string, -=transparent
BASE_MASK = [
    "----------------",
    "-----------BBBB-",
    "--------BBBBBBBB",
    "------BBBBBBBBB-",
    "-----BBBBBB--B--",
    "----BBSB----B---",
    "---BBSB----B----",
    "---BBB----B-----",
    "--BBB----B------",
    "--BBB---B-------",
    "--BBB--B--------",
    "-BBB--B---------",
    "-BBB-B----------",
    "-BBBB-----------",
    "-BBB------------",
    "--B-------------",
]

PULL0_MASK = [
    "-S--------------",
    "-SS--------BBBB-",
    "--BB----BBBBBBBB",
    "---BB-BBBBBBBBB-",
    "----BBBBBBB---B-",
    "----BBBB-----B--",
    "---BBSBB-----B--",
    "---BBB-BB---B---",
    "--BBB---BB-B----",
    "--BBB----BBB----",
    "--BBB-----B-----",
    "-BBB----BB------",
    "-BBB---B--------",
    "-BBB-BB---------",
    "-BBBB-----------",
    "--B-------------",
]

PULL1_MASK = [
    "----------------",
    "--S---------BBB-",
    "--SS----BBBBBBBB",
    "---BB-BBBBBBBBB-",
    "----BBBBBBBB--B-",
    "----BBBB------B-",
    "---BBSBB-----B--",
    "---BBB-BB----B--",
    "--BBB---BB--B---",
    "--BBB----BB-B---",
    "--BBB-----BB----",
    "--BBB-----B-----",
    "-BBB----BB------",
    "-BBB--BB--------",
    "-BBBBB----------",
    "--B-------------",
]

# Pull_2: extend pull_1 string further left
PULL2_MASK = [
    "----------------",
    "---S--------BBB-",
    "---SS---BBBBBBBB",
    "----BBBBBBBBBBB-",
    "----BBBBBBBB--B-",
    "----BBBB------B-",
    "---BBSBB-----B--",
    "---BBB-BB----B--",
    "--BBB---BB--B---",
    "--BBB----BB-B---",
    "--BBB-----BB----",
    "--BBB-----B-----",
    "-BBB----BB------",
    "-BBB--BB--------",
    "-BBBB-----------",
    "--B-------------",
]

# Color schemes for each bow
BOWS = {
    'crude':   {'B': (110, 70, 30),  'G': (75, 45, 18),  'S': (180,180,180)},
    'hunting': {'B': (160, 105, 55), 'G': (110, 70, 30), 'S': (190,190,190)},
    'recurve': {'B': (185, 130, 65), 'G': (135, 90, 40), 'S': (200,200,200)},
    'compound':{'B': (90, 110, 135), 'G': (60, 75, 95),  'S': (210,210,225)},
}

def draw(mask, colors):
    img = Image.new('RGBA', (16,16), (0,0,0,0))
    px = img.load()
    for y in range(16):
        row = mask[y]
        for x, ch in enumerate(row):
            if ch in colors:
                r,g,b = colors[ch]
                px[x,y] = (r,g,b,255)
    return img

stage_names = ['bow', 'bow_pulling_0', 'bow_pulling_1', 'bow_pulling_2']
masks = [BASE_MASK, PULL0_MASK, PULL1_MASK, PULL2_MASK]

for name, colors in BOWS.items():
    for si, mask in enumerate(masks):
        img = draw(mask, colors)
        path = os.path.join(OUT, f'sixty_seconds_{name}_{stage_names[si]}.png')
        img.save(path)
        print(f'{name}_{stage_names[si]}')

print('\nDone!')
