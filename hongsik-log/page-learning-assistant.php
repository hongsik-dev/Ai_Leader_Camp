<?php
/**
 * Template Name: AI 학습 기록 도우미
 *
 * @package HongsikLog
 */

get_header();
?>

<article class="learning-assistant-page">
	<header class="archive-heading">
		<p class="archive-kicker">MVP TOOL</p>
		<h1 class="archive-title"><?php the_title(); ?></h1>
		<p class="assistant-intro">오늘 배운 내용을 입력하면 블로그 초안과 추천 태그를 바로 만드는 작은 학습 기록 도구입니다.</p>
	</header>

	<section class="assistant-workbench" aria-label="<?php esc_attr_e( 'AI learning log assistant', 'hongsik-log' ); ?>">
		<form class="assistant-form" id="learning-assistant-form">
			<div class="assistant-panel">
				<div class="assistant-panel-head">
					<p class="section-label">INPUT</p>
					<h2>오늘의 학습 메모</h2>
				</div>

				<label class="assistant-field">
					<span>학습 키워드</span>
					<input type="text" id="assistant-topic" name="topic" placeholder="예: MVP, PRD, 프롬프트 설계">
				</label>

				<label class="assistant-field">
					<span>오늘 배운 내용</span>
					<textarea id="assistant-summary" name="summary" rows="5" placeholder="오늘 이해한 개념을 편하게 적어보세요."></textarea>
				</label>

				<label class="assistant-field">
					<span>어려웠던 점 또는 에러</span>
					<textarea id="assistant-problem" name="problem" rows="4" placeholder="막혔던 지점, 헷갈렸던 개념, 에러 메시지를 적어보세요."></textarea>
				</label>

				<label class="assistant-field">
					<span>해결한 방법</span>
					<textarea id="assistant-solution" name="solution" rows="4" placeholder="어떻게 해결했는지, 아직 못 풀었다면 다음 시도까지 적어보세요."></textarea>
				</label>

				<label class="assistant-field">
					<span>느낀 점</span>
					<textarea id="assistant-reflection" name="reflection" rows="4" placeholder="오늘 배운 것을 내 일이나 목표와 연결해보세요."></textarea>
				</label>

				<div class="assistant-row">
					<label class="assistant-field">
						<span>내 업무 스타일</span>
						<select id="assistant-style" name="style">
							<option value="빠른 실행형 실험가">빠른 실행형 실험가</option>
							<option value="구조 설계형 전략가">구조 설계형 전략가</option>
							<option value="창의 협업형 메이커">창의 협업형 메이커</option>
						</select>
					</label>

					<label class="assistant-field">
						<span>희망 발행일</span>
						<input type="date" id="assistant-date" name="date">
					</label>
				</div>

				<label class="assistant-field">
					<span>다음 액션</span>
					<textarea id="assistant-next" name="next" rows="3" placeholder="내일 이어서 할 일이나 확인할 것을 적어보세요."></textarea>
				</label>

				<div class="assistant-actions">
					<button class="assistant-primary" type="submit">초안 만들기</button>
					<button class="assistant-quiet" type="button" id="assistant-clear">입력 비우기</button>
				</div>
				<p class="assistant-save-state" id="assistant-save-state" aria-live="polite">입력 내용은 이 브라우저에 자동 저장됩니다.</p>
			</div>

			<div class="assistant-panel assistant-output-panel">
				<div class="assistant-panel-head">
					<p class="section-label">OUTPUT</p>
					<h2>생성 결과</h2>
				</div>

				<div class="assistant-output-tabs" role="tablist" aria-label="<?php esc_attr_e( 'Generated output sections', 'hongsik-log' ); ?>">
					<button class="is-active" type="button" data-assistant-tab="draft">블로그 초안</button>
					<button type="button" data-assistant-tab="prompt">보강 프롬프트</button>
					<button type="button" data-assistant-tab="plan">발행 계획</button>
				</div>

				<div class="assistant-output is-active" data-assistant-panel="draft">
					<div class="assistant-output-head">
						<h3>블로그 초안</h3>
						<button type="button" data-copy-target="assistant-draft">복사</button>
					</div>
					<textarea id="assistant-draft" readonly rows="18"></textarea>
				</div>

				<div class="assistant-output" data-assistant-panel="prompt">
					<div class="assistant-output-head">
						<h3>ChatGPT 보강 프롬프트</h3>
						<button type="button" data-copy-target="assistant-prompt">복사</button>
					</div>
					<textarea id="assistant-prompt" readonly rows="18"></textarea>
				</div>

				<div class="assistant-output" data-assistant-panel="plan">
					<div class="assistant-output-head">
						<h3>추천 태그와 발행 체크리스트</h3>
						<button type="button" id="assistant-download">Markdown 저장</button>
					</div>
					<div class="assistant-tag-box" id="assistant-tags" aria-label="<?php esc_attr_e( 'Recommended tags', 'hongsik-log' ); ?>"></div>
					<div class="assistant-plan-box" id="assistant-plan"></div>
				</div>
			</div>
		</form>
	</section>
</article>

<?php
get_footer();
