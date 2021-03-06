/*
 * Copyright (C) 2012  Krawler Information Systems Pvt Ltd
 * All rights reserved.
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/
Wtf.KWLTagSearch = function(config){
    Wtf.KWLTagSearch.superclass.constructor.call(this, config);
}
Wtf.extend(Wtf.KWLTagSearch, Wtf.form.TextField, {
    Store: null,
    emptyText  : WtfGlobal.getLocaleText('lang.search.text'),
    StorageArray: null,
    limit: this.limit,
    initComponent: function(){
        Wtf.KWLTagSearch.superclass.initComponent.call(this);
        this.addEvents({
            'SearchComplete': true
        });
    },
    timer:new Wtf.util.DelayedTask(this.callKeyUp),
    setPage: function(val) {
        this.limit = val;
    },
    onRender: function(ct, position){
        Wtf.KWLTagSearch.superclass.onRender.call(this, ct, position);
        this.el.dom.onkeyup = this.onKeyUp.createDelegate(this);
    },
    onKeyUp: function(e){
        if(this.Store) {
            if (this.getValue() != "") {
                this.timer.cancel();
                this.timer.delay(1000,this.callKeyUp,this);
            }
            else {
                this.Store.reload({
                    params: {
                        start: 0,
                        limit: this.limit,
                        ss: ""
                    }
                });
            }
            this.fireEvent('SearchComplete', this.Store);
        }
    },
    callKeyUp: function() {
        
      this.Store.reload({
          params: {
              start: 0,
              limit: this.limit,
              ss: this.getValue()
          }
      });
    },
    StorageChanged: function(store){
        this.Store = store;
        this.StorageArray = this.Store.getRange();
    }
});
Wtf.reg('KWLTagSearch', Wtf.KWLTagSearch);
