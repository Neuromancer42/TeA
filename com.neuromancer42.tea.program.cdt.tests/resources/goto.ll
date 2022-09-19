; ModuleID = 'goto.c'
source_filename = "goto.c"
target datalayout = "e-m:o-p270:32:32-p271:32:32-p272:64:64-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-apple-macosx12.0.0"

; Function Attrs: noinline nounwind optnone ssp uwtable
define i32 @main() #0 {
entry:
  %retval = alloca i32, align 4
  %a = alloca i32, align 4
  store i32 0, i32* %retval, align 4
  store i32 10, i32* %a, align 4
  %0 = load i32, i32* %a, align 4
  %cmp = icmp eq i32 %0, 1
  br i1 %cmp, label %if.then, label %if.end

if.then:                                          ; preds = %entry
  br label %case_1

if.end:                                           ; preds = %entry
  store i32 0, i32* %retval, align 4
  br label %return

case_1:                                           ; preds = %if.then
  store i32 1, i32* %retval, align 4
  br label %return

return:                                           ; preds = %case_1, %if.end
  %1 = load i32, i32* %retval, align 4
  ret i32 %1
}

; Function Attrs: noinline nounwind optnone ssp uwtable
define void @foo(i64 %tmp___1) #0 {
entry:
  %tmp___1.addr = alloca i64, align 8
  %n = alloca i64, align 8
  %cNext = alloca i32, align 4
  store i64 %tmp___1, i64* %tmp___1.addr, align 8
  store i32 10, i32* %cNext, align 4
  br label %while.body

while.body:                                       ; preds = %entry, %if.end7
  br label %while_continue

while_continue:                                   ; preds = %while_break___1, %while.body
  %0 = load i64, i64* %tmp___1.addr, align 8
  %tobool = icmp ne i64 %0, 0
  br i1 %tobool, label %if.then, label %if.end7

if.then:                                          ; preds = %while_continue
  store i64 1, i64* %n, align 8
  br label %while.body2

while.body2:                                      ; preds = %if.then, %if.end6
  br label %while_continue___1

while_continue___1:                               ; preds = %while.body2
  %1 = load i64, i64* %n, align 8
  %inc = add nsw i64 %1, 1
  store i64 %inc, i64* %n, align 8
  %2 = load i32, i32* %cNext, align 4
  %cmp = icmp ne i32 %2, 10
  br i1 %cmp, label %if.then3, label %if.else

if.then3:                                         ; preds = %while_continue___1
  %3 = load i32, i32* %cNext, align 4
  %cmp4 = icmp ne i32 %3, 0
  br i1 %cmp4, label %if.end, label %if.then5

if.then5:                                         ; preds = %if.then3
  br label %while_break___1

if.end:                                           ; preds = %if.then3
  br label %if.end6

if.else:                                          ; preds = %while_continue___1
  br label %while_break___1

if.end6:                                          ; preds = %if.end
  br label %while.body2

while_break___1:                                  ; preds = %if.else, %if.then5
  %4 = load i64, i64* %n, align 8
  %sub = sub nsw i64 0, %4
  call void @foo(i64 %sub)
  br label %while_continue

if.end7:                                          ; preds = %while_continue
  br label %while.body
}

attributes #0 = { noinline nounwind optnone ssp uwtable "darwin-stkchk-strong-link" "frame-pointer"="all" "min-legal-vector-width"="0" "no-trapping-math"="true" "probe-stack"="___chkstk_darwin" "stack-protector-buffer-size"="8" "target-cpu"="penryn" "target-features"="+cx16,+cx8,+fxsr,+mmx,+sahf,+sse,+sse2,+sse3,+sse4.1,+ssse3,+x87" "tune-cpu"="generic" }

!llvm.module.flags = !{!0, !1, !2, !3, !4}
!llvm.ident = !{!5}

!0 = !{i32 2, !"SDK Version", [2 x i32] [i32 12, i32 3]}
!1 = !{i32 1, !"wchar_size", i32 4}
!2 = !{i32 7, !"PIC Level", i32 2}
!3 = !{i32 7, !"uwtable", i32 1}
!4 = !{i32 7, !"frame-pointer", i32 2}
!5 = !{!"Apple clang version 13.1.6 (clang-1316.0.21.2.5)"}
