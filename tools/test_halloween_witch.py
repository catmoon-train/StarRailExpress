"""玩家魔女资源检查；不覆盖皮肤，不修改项目文件。"""
import hashlib
import sys
import unittest

sys.dont_write_bytecode = True
import numpy as np
from PIL import Image
from generate_halloween_witch import SKIN, TEXTURE, accessory_texture


class WitchResourcesTest(unittest.TestCase):
    def test_skin_is_the_exact_user_supplied_png(self):
        self.assertEqual(hashlib.sha256(SKIN.read_bytes()).hexdigest(),
                         '8f843a3c65776b3e1ef6afd92f4bff4cbc794a107bccf3e813b9119dccb3ce00')
        with Image.open(SKIN) as image:
            self.assertEqual(image.size, (64, 64))

    def test_skin_retains_alpha_and_slim_arm_unused_pixels(self):
        skin = Image.open(SKIN)
        self.assertEqual(skin.mode, 'RGBA')
        self.assertEqual(skin.crop((54, 20, 56, 32)).getchannel('A').getextrema(), (0, 0))
        self.assertGreater(np.asarray(skin)[8:16, 8:16, 3].sum(), 0, '脸部 UV 必须保留')

    def test_accessory_texture_is_independent_and_reproducible(self):
        gear = Image.open(TEXTURE)
        self.assertEqual(gear.size, (128, 128))
        np.testing.assert_array_equal(np.asarray(gear), np.asarray(accessory_texture()))
        self.assertEqual(set(np.unique(np.asarray(gear)[:, :, 3])), {0, 255})

    def test_hat_lining_and_pumpkin_face_have_their_own_materials(self):
        with Image.open(TEXTURE) as gear:
            self.assertNotEqual(gear.getpixel((64, 0)), gear.getpixel((64, 24)))
            self.assertEqual(gear.getpixel((6, 102)), (102, 52, 46, 255))
            self.assertNotEqual(gear.getpixel((6, 102)), gear.getpixel((5, 102)))


if __name__ == '__main__':
    unittest.main(verbosity=2)
