<?php
/**
 * Theme setup and helpers.
 *
 * @package HongsikLog
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

function hongsik_log_setup() {
	load_theme_textdomain( 'hongsik-log', get_template_directory() . '/languages' );

	add_theme_support( 'automatic-feed-links' );
	add_theme_support( 'title-tag' );
	add_theme_support( 'post-thumbnails' );
	add_theme_support( 'responsive-embeds' );
	add_theme_support(
		'html5',
		array(
			'search-form',
			'comment-form',
			'comment-list',
			'gallery',
			'caption',
			'style',
			'script',
		)
	);

	register_nav_menus(
		array(
			'primary' => __( 'Primary Menu', 'hongsik-log' ),
		)
	);
}
add_action( 'after_setup_theme', 'hongsik_log_setup' );

function hongsik_log_enqueue_assets() {
	wp_enqueue_style(
		'hongsik-log-style',
		get_stylesheet_uri(),
		array(),
		filemtime( get_stylesheet_directory() . '/style.css' )
	);

	if ( is_page_template( 'page-learning-map.php' ) || is_page( 'learning-map' ) ) {
		wp_enqueue_script(
			'hongsik-log-learning-map',
			get_template_directory_uri() . '/assets/learning-map.js',
			array(),
			filemtime( get_stylesheet_directory() . '/assets/learning-map.js' ),
			true
		);
	}

	if ( is_page_template( 'page-learning-assistant.php' ) || is_page( 'learning-assistant' ) ) {
		wp_enqueue_script(
			'hongsik-log-learning-assistant',
			get_template_directory_uri() . '/assets/learning-assistant.js',
			array(),
			filemtime( get_stylesheet_directory() . '/assets/learning-assistant.js' ),
			true
		);
	}
}
add_action( 'wp_enqueue_scripts', 'hongsik_log_enqueue_assets' );

function hongsik_log_order_learning_posts( $query ) {
	if ( is_admin() || ! $query->is_main_query() ) {
		return;
	}

	if ( $query->is_home() || $query->is_category() || $query->is_tag() || $query->is_date() ) {
		$query->set( 'orderby', 'date' );
		$query->set( 'order', 'ASC' );
		$query->set( 'posts_per_page', -1 );
		$query->set( 'nopaging', true );
	}
}
add_action( 'pre_get_posts', 'hongsik_log_order_learning_posts' );

function hongsik_log_icon( $name ) {
	$icons = array(
		'home'    => '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M4 10.8 12 4l8 6.8v8.7a.5.5 0 0 1-.5.5h-5v-5.8h-5V20h-5a.5.5 0 0 1-.5-.5v-8.7Z"/></svg>',
		'tag'     => '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M4 5.5A1.5 1.5 0 0 1 5.5 4h6.1c.4 0 .8.2 1.1.4l7 7a1.5 1.5 0 0 1 0 2.2l-6.1 6.1a1.5 1.5 0 0 1-2.2 0l-7-7c-.3-.3-.4-.7-.4-1.1V5.5Zm4.4 2a1.4 1.4 0 1 0 0 2.8 1.4 1.4 0 0 0 0-2.8Z"/></svg>',
		'archive' => '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M4 5.5A1.5 1.5 0 0 1 5.5 4h13A1.5 1.5 0 0 1 20 5.5V8H4V5.5Zm1.5 4H18.5V19A1.5 1.5 0 0 1 17 20H7a1.5 1.5 0 0 1-1.5-1.5v-9ZM9 12v1.5h6V12H9Z"/></svg>',
		'github'  => '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M7 5.5a2.5 2.5 0 1 1 3.2 2.4v2.4h3.6V7.9a2.5 2.5 0 1 1 1.8 0v3.3c0 .5-.4.9-.9.9h-4.5v4a2.5 2.5 0 1 1-1.8 0V7.9A2.5 2.5 0 0 1 7 5.5Z"/></svg>',
		'rss'     => '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M6.3 16.2a2.3 2.3 0 1 1 0 4.6 2.3 2.3 0 0 1 0-4.6ZM4 9.8c5.6 0 10.2 4.6 10.2 10.2h-2.7A7.5 7.5 0 0 0 4 12.5V9.8Zm0-5.8c8.8 0 16 7.2 16 16h-2.8C17.2 12.7 11.3 6.8 4 6.8V4Z"/></svg>',
		'about'   => '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M12 12.6a4.2 4.2 0 1 0 0-8.4 4.2 4.2 0 0 0 0 8.4Zm-7 7.2c.7-3.4 3.5-5.5 7-5.5s6.3 2.1 7 5.5a.7.7 0 0 1-.7.8H5.7a.7.7 0 0 1-.7-.8Z"/></svg>',
		'map'     => '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M5.5 5a2.5 2.5 0 1 1 2 4l1.9 3.2a2.5 2.5 0 0 1 3.4.2l3-2.7a2.5 2.5 0 1 1 1.1 1.2l-3 2.7a2.5 2.5 0 0 1-.1 2.8l2 2.1a2.5 2.5 0 1 1-1.2 1.1l-2-2.1a2.5 2.5 0 0 1-3.3-3.9L7.3 9.8A2.5 2.5 0 0 1 5.5 5Z"/></svg>',
		'write'   => '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M5 18.8 6.1 14 15.7 4.4a2 2 0 0 1 2.8 0l1.1 1.1a2 2 0 0 1 0 2.8L10 17.9 5 18.8Zm1 1.2h13v-1.7H6V20Z"/></svg>',
	);

	return isset( $icons[ $name ] ) ? $icons[ $name ] : '';
}

function hongsik_log_excerpt_length() {
	return 46;
}
add_filter( 'excerpt_length', 'hongsik_log_excerpt_length' );

function hongsik_log_excerpt_more() {
	return '...';
}
add_filter( 'excerpt_more', 'hongsik_log_excerpt_more' );

function hongsik_log_post_count() {
	$count = wp_count_posts( 'post' );
	return isset( $count->publish ) ? (int) $count->publish : 0;
}

function hongsik_log_get_about_page() {
	$page = get_page_by_path( 'about' );
	return $page instanceof WP_Post ? $page : null;
}

function hongsik_log_get_learning_map_page() {
	$page = get_page_by_path( 'learning-map' );
	return $page instanceof WP_Post ? $page : null;
}

function hongsik_log_get_learning_assistant_page() {
	$page = get_page_by_path( 'learning-assistant' );
	return $page instanceof WP_Post ? $page : null;
}

function hongsik_log_render_tags( $limit = 60 ) {
	$tags = get_tags(
		array(
			'hide_empty' => true,
			'orderby'    => 'count',
			'order'      => 'DESC',
			'number'     => $limit,
		)
	);

	echo '<ul class="tag-list">';
	echo '<li><a href="' . esc_url( home_url( '/' ) ) . '">all <span class="count">( ' . esc_html( hongsik_log_post_count() ) . ' )</span></a></li>';

	if ( empty( $tags ) ) {
		echo '</ul>';
		echo '<p class="tag-empty">' . esc_html__( '글을 발행하면 태그가 이곳에 쌓입니다.', 'hongsik-log' ) . '</p>';
		return;
	}

	foreach ( $tags as $tag ) {
		printf(
			'<li><a href="%1$s">%2$s <span class="count">( %3$d )</span></a></li>',
			esc_url( get_tag_link( $tag ) ),
			esc_html( $tag->name ),
			(int) $tag->count
		);
	}

	echo '</ul>';
}

function hongsik_log_render_post_tags() {
	$tags = get_the_tags();

	if ( empty( $tags ) ) {
		return;
	}

	echo '<div class="post-tags" aria-label="' . esc_attr__( 'Post tags', 'hongsik-log' ) . '">';
	foreach ( $tags as $tag ) {
		printf(
			'<a href="%1$s">%2$s</a>',
			esc_url( get_tag_link( $tag ) ),
			esc_html( $tag->name )
		);
	}
	echo '</div>';
}
