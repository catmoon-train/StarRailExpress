/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.utils.lottery;

import java.util.List;

public class LotteryPoolsConfig {
    public static class PoolConfig{
        public static class QualityListItemConfig {
            public Double getProbability() {
                return Probability;
            }
            public void setProbability(Double probability) {
                this.Probability = probability;
            }
            public List<String> getItemList() {
                return ItemList;
            }
            public void setItemList(List<String> itemList) {
                this.ItemList = itemList;
            }
            /** 该品质的概率 */
            private Double Probability;
            /** 该品质的物品列表 */
            private List<String> ItemList;
        }
        public int getPoolID() {
            return PoolID;
        }
        public void setPoolID(int poolID) {
            this.PoolID = poolID;
        }
        public void setEnable(boolean enable) {
            this.Enable = enable;
        }
        public boolean isEnable() {
            return Enable;
        }
        public String getPoolName() {
            return PoolName;
        }
        public void setPoolName(String poolName) {
            this.PoolName = poolName;
        }
        public String getPoolType() {
            return PoolType;
        }
        public void setPoolType(String poolType) {
            this.PoolType = poolType;
        }
        public List<QualityListItemConfig> getQualityListGroup() {
            return QualityListGroup;
        }
        public void setQualityListGroup(List<QualityListItemConfig> qualityListGroup) {
            this.QualityListGroup = qualityListGroup;
        }
        private int PoolID;
        private boolean Enable;
        private String PoolName;
        private String PoolType;
        private List<QualityListItemConfig> QualityListGroup;
    }
    public List<PoolConfig> getPools() {
        return Pools;
    }
    public void setPools(List<PoolConfig> pools) {
        this.Pools = pools;
    }
    private List<PoolConfig> Pools;
}
