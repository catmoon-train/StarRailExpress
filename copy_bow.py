from PIL import Image
import os

DL = r"C:\Users\hai'man\Downloads"
OUT = r'D:\gengxin\StarRailExpress\src\main\resources\assets\noellesroles\textures\item'

# Load reference images
base = Image.open(DL + r"\Bow_JE2_BE1.png").convert('RGBA')
p0   = Image.open(DL + r"\Bow_(Pull_0)_JE1_BE1.webp").convert('RGBA').resize((16,16), Image.NEAREST)
p1   = Image.open(DL + r"\Bow_(Pull_1)_JE1_BE1.webp").convert('RGBA').resize((16,16), Image.NEAREST)

# pull_2: shift the string further left from p1
p2 = Image.new('RGBA', (16,16), (0,0,0,0))
pp1 = p1.load()
pp0 = p0.load()
ppbase = base.load()
pp2 = p2.load()
for y in range(16):
    for x in range(16):
        r,g,b,a = pp1[x,y]
        if a > 128:
            bright = (r+g+b)/3
            if bright > 150:  # string pixel - shift it left
                nx = max(0, x-1)
                if pp2[nx,y][3] < 128:
                    pp2[nx,y] = (r,g,b,a)
            else:
                pp2[x,y] = (r,g,b,a)

stages = [base, p0, p1, p2]

# Extract pixel masks from base bow
# String = bright pixels, body = dark pixels
def get_mask(img):
    mask = []
    px = img.load()
    for y in range(16):
        row = ''
        for x in range(16):
            r,g,b,a = px[x,y]
            if a > 128:
                bright = (r+g+b)/3
                if bright > 150:
                    row += 'S'
                elif g > r and g > b and r < 100:
                    row += 'G'  # green-ish grip area
                else:
                    row += 'B'
            else:
                row += '-'
        mask.append(row)
    return mask

base_mask = get_mask(base)

print('Base bow pixel map:')
for row in base_mask:
    print(f'"{row}",')

# Now generate for all bows with different colors
BOWS = {
    'crude':   {'B': (110, 70, 30,255),  'G': (75, 45, 18,255), 'S': (200,200,200,255)},
    'hunting': {'B': (160, 105, 55,255), 'G': (110, 70, 30,255),'S': (202,202,202,255)},
    'recurve': {'B': (185, 130, 65,255), 'G': (135, 90, 40,255),'S': (205,205,205,255)},
    'compound':{'B': (90, 110, 135,255), 'G': (60, 75, 95,255), 'S': (210,210,225,255)},
}

stage_names = ['bow', 'bow_pulling_0', 'bow_pulling_1', 'bow_pulling_2']

for name, colors in BOWS.items():
    # Get masks for each stage from the reference
    for si, stage in enumerate(stages):
        img = Image.new('RGBA', (16,16), (0,0,0,0))
        px_out = img.load()
        px_ref = stage.load()
        for y in range(16):
            for x in range(16):
                r,g,b,a = px_ref[x,y]
                if a > 128:
                    bright = (r+g+b)/3
                    if bright > 150:
                        px_out[x,y] = colors['S']
                    elif g > r and g > b and r < 100:
                        px_out[x,y] = colors['G']
                    else:
                        px_out[x,y] = colors['B']
        out_path = os.path.join(OUT, f'sixty_seconds_{name}_{stage_names[si]}.png')
        img.save(out_path)
        print(f'{name}_{stage_names[si]}: saved')

print('\nAll textures generated!')
