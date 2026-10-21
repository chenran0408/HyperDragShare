package com.leaf.hyperdragshare.codex;

interface ISidebarContent {
    oneway void resize(int width, int height);
    oneway void back();
    oneway void close();
}
